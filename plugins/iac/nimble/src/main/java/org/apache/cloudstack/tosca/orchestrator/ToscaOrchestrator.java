// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.tosca.orchestrator;

import com.cloud.api.ApiDispatcher;
import com.cloud.api.ApiGsonHelper;
import com.cloud.api.ApiSerializerHelper;
import com.cloud.api.ApiServer;
import com.cloud.api.response.ApiResponseSerializer;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseAsyncCreateCmd;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.jobs.AsyncJob;
import org.apache.cloudstack.framework.jobs.AsyncJobDispatcher;
import org.apache.cloudstack.framework.jobs.AsyncJobExecutionContext;
import org.apache.cloudstack.framework.jobs.AsyncJobManager;
import org.apache.cloudstack.framework.jobs.Outcome;
import org.apache.cloudstack.framework.jobs.impl.AsyncJobVO;
import org.apache.cloudstack.framework.jobs.impl.OutcomeImpl;
import org.apache.cloudstack.jobs.JobInfo;
import org.apache.cloudstack.managed.context.ManagedContextExecutor;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeVO;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;
import org.apache.cloudstack.tosca.parser.ToscaParser;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ToscaOrchestrator {
    private final Logger logger = LogManager.getLogger(ToscaOrchestrator.class);

    @Inject
    private ToscaParser toscaParser;

    @Inject
    private ApiDispatcher apiDispatcher;

    @Inject
    private AsyncJobManager asyncJobManager;

    @Inject
    @Named("ApiAsyncJobDispatcher")
    private AsyncJobDispatcher asyncJobDispatcher;

    @Inject
    private ApiServer apiServer;

    @Inject
    private EntityManager entityManager;

    private Map<String, ToscaNodeType> toscaProfile;

    private ExecutorService executorPool;

    public void deployIacTemplate(String iacTemplateContent) {
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(iacTemplateContent, toscaProfile, null);
        Map<String, CompletableFuture<String>> provisioningTasksFutures = createProvisioningTasksFutures(serviceTemplate);
        logger.debug("Awaiting for all the provisioning tasks of the node template to complete.");
        CompletableFuture<Void> serviceTemplateFeature = CompletableFuture.allOf(provisioningTasksFutures.values().toArray(new CompletableFuture[0]));
        serviceTemplateFeature.join();
        logger.info("All provisioning tasks have completed successfully.");
    }

    private Map<String, CompletableFuture<String>> createProvisioningTasksFutures(ToscaServiceTemplate serviceTemplate) {
        logger.debug("Building provisioning tasks for the service template based on its graph topological sort.");
        Map<String, CompletableFuture<String>> futures = new HashMap<>();
        CallContext callContext = CallContext.current();
        getServiceTemplateTopologicalSort(serviceTemplate).forEach((node, dependencies) -> {
            ToscaNodeTemplate nodeTemplate = serviceTemplate.getNodeTemplates().get(node);
            CompletableFuture<String> taskFuture;
            if (dependencies.isEmpty()) {
                logger.debug("Node [{}] has no dependencies. Building its provisioning task, which will be ready to be allocated for execution.", node);
                taskFuture = buildNodeProvisioningTask(nodeTemplate, callContext);
            } else {
                logger.debug("Node [{}] has [{}] dependencies. Building its provisioning task, which will only be allocated for execution when all dependencies are ready.", node, dependencies.size());
                CompletableFuture<?>[] dependenciesFutures = dependencies.stream()
                        .map((dep) -> futures.get(dep.getName())).toArray(CompletableFuture[]::new);
                taskFuture = CompletableFuture.allOf(dependenciesFutures).thenCompose(v -> {
                    logger.debug("All dependencies of the node [{}] are ready. Building its provisioning task.", node);
                    return buildNodeProvisioningTask(nodeTemplate, callContext);
                });
            }

            futures.put(node, taskFuture);
        });

        logger.debug("All provisioning tasks for the service template have been built successfully.");
        return futures;
    }

    // O(|V|+|E|)
    private LinkedHashMap<String, Set<ToscaNodeTemplate>> getServiceTemplateTopologicalSort(ToscaServiceTemplate serviceTemplate) {
        LinkedHashMap<String, Set<ToscaNodeTemplate>> topologicalSort = new LinkedHashMap<>();
        Set<String> visitedNodes = new HashSet<>();
        Set<String> branchAncestors = new HashSet<>();
        for (ToscaNodeTemplate node : serviceTemplate.getNodeTemplates().values()) {
            if (!visitedNodes.contains(node.getName())) {
                depthFirstSearch(topologicalSort, node.getName(), serviceTemplate.getDependencyGraph(), branchAncestors, visitedNodes);
            }
        }
        return topologicalSort;
    }

    private void depthFirstSearch(LinkedHashMap<String, Set<ToscaNodeTemplate>> topologicalSort, String node, Map<String, Set<ToscaNodeTemplate>> graph, Set<String> branchAncestors, Set<String> visitedNodes) {
        visitedNodes.add(node);
        branchAncestors.add(node);
        for (ToscaNodeTemplate dependency : graph.getOrDefault(node, Collections.emptySet())) {
            if (branchAncestors.contains(dependency.getName())) {
                logger.error("A cycle was detected in the service template graph. Aborting IaC template deployment.");
                throw new InvalidParameterValueException("A cycle was detected in the service template graph. Please, ensure that the service template graph is acyclic.");
            }

            if (!visitedNodes.contains(dependency.getName())) {
                depthFirstSearch(topologicalSort, dependency.getName(), graph, branchAncestors, visitedNodes);
            }
        }

        branchAncestors.remove(node);
        logger.debug("Node [{}] has been added to the topological sort.", node);
        topologicalSort.put(node, graph.getOrDefault(node, Collections.emptySet()));
    }

    private CompletableFuture<String> buildNodeProvisioningTask(ToscaNodeTemplate nodeTemplate, CallContext callContext) {
        return provisionNode(nodeTemplate, callContext)
//                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(result -> {
                    logger.debug("SUCCESS [{}] [{}]", nodeTemplate.getName(), Thread.currentThread().getName());
                    return result;
                }).exceptionally(ex -> {
                    logger.debug("FAILURE [{}] [{}]", nodeTemplate.getName(), ex);
                    throw new CompletionException(ex);
                });
    }

    private CompletableFuture<String> provisionNode(ToscaNodeTemplate nodeTemplate, CallContext callContext) {
        return CompletableFuture.supplyAsync(() -> {
            CallContext.register(callContext, null);
            ManagedContextExecutor.execute(() -> {
                dispatchProvisioningCommand(nodeTemplate, callContext);
            });
            return "res-" + nodeTemplate.getName();
        }, executorPool);
    }

    private void dispatchProvisioningCommand(ToscaNodeTemplate nodeTemplate, CallContext callContext) {
        Class<?> apiClass = apiServer.getCmdClass(nodeTemplate.getType().getProvisioningApi());
        try {
            Object cmd = apiClass.getDeclaredConstructor().newInstance();
            Map<String, Object> provisioningResult;
            if (cmd instanceof BaseAsyncCreateCmd) {
                provisioningResult = dispatchProvisioningAsynchronousCommand((BaseAsyncCreateCmd) cmd, nodeTemplate.getApiParams(), callContext);
            } else if (cmd instanceof BaseCmd && !(cmd instanceof BaseAsyncCmd)) {
                provisioningResult = dispatchProvisioningSynchronousCommand((BaseCmd) cmd, nodeTemplate.getApiParams());
            } else {
                throw new CloudRuntimeException(String.format("The provisioning API associated with the node template [%s] is not available.", nodeTemplate.getName()));
            }
            logger.info("Result of the [{}] execution: {}.", nodeTemplate.getName(), provisioningResult);
            nodeTemplate.resolveAttributes(provisioningResult);
        } catch (Exception e) {
            logger.error("Could not instantiate the API class [{}]: {}.", apiClass.getName(), e.getMessage());
            throw new InvalidParameterValueException(String.format("Could not dispatch the provisioning task of [%s]. Please, check the availability of the API associated with it.", nodeTemplate.getName()));
        }
    }

    private Map<String, Object> dispatchProvisioningSynchronousCommand(BaseCmd syncCmd, Map<String, String> apiParams) throws Exception {
        logger.info("Dispatching the provisioning synchronous command [{}] with the following parameters {}.", syncCmd.getClass().getName(), apiParams);
        syncCmd = ComponentContext.inject(syncCmd);
        apiDispatcher.dispatch(syncCmd, apiParams, false);
        return ApiSerializerHelper.fromSerializedStringToMap(ApiResponseSerializer.toSerializedString((ResponseObject) syncCmd.getResponseObject(), BaseCmd.RESPONSE_TYPE_JSON));
    }

    private Map<String, Object> dispatchProvisioningAsynchronousCommand(BaseAsyncCreateCmd asyncCmd, Map<String, String> apiParams, CallContext callContext) throws Exception {
        logger.info("Dispatching the provisioning asynchronous command [{}] with the following parameters {}.", asyncCmd.getClass().getName(), apiParams);
        AsyncJobExecutionContext executionContext = AsyncJobExecutionContext.getCurrentExecutionContext();
        try {
            asyncCmd = ComponentContext.inject(asyncCmd);
            logger.debug("Dispatching the create workflow for the command [{}].", asyncCmd.getClass().getName());
            apiDispatcher.dispatchCreateCmd(asyncCmd, apiParams);

            logger.debug("Successfully executed the create workflow for the command [{}]. Thus, dispatching its async job.", asyncCmd.getClass().getName());
            AsyncJobVO job = dispatchAsyncJob(asyncCmd, apiParams, callContext);
            executionContext.joinJob(job.getId());
            Outcome<String> outcome = new NodeTemplateProvisioningOutcome(job);
            return ApiSerializerHelper.fromSerializedStringToMap(outcome.get());
        } finally {
            if (executionContext.getJob() != null) {
                asyncJobManager.expungeAsyncJob((AsyncJobVO) executionContext.getJob());
            }
        }
    }

    private AsyncJobVO dispatchAsyncJob(BaseAsyncCreateCmd asyncCmd, Map<String, String> apiParams, CallContext callContext) {
        apiParams.put("ctxStartEventId", "1");
        Long objectId = ObjectUtils.defaultIfNull(asyncCmd.getEntityId(), asyncCmd.getApiResourceId());
        apiParams.put("id", objectId.toString());
        apiParams.put("ctxUserId", String.valueOf(callContext.getCallingUserId()));
        apiParams.put("ctxAccountId", String.valueOf(callContext.getCallingAccountId()));

        AsyncJobVO job = new AsyncJobVO("", callContext.getCallingUserId(), callContext.getCallingAccountId(), asyncCmd.getClass().getName(),
                ApiGsonHelper.getBuilder().create().toJson(apiParams), objectId,
                asyncCmd.getApiResourceType() != null ? asyncCmd.getApiResourceType().toString() : null,
                null);
        job.setDispatcher(asyncJobDispatcher.getName());
        asyncJobManager.submitAsyncJob(job);
        return job;
    }

    private class NodeTemplateProvisioningOutcome extends OutcomeImpl<String> {
        private final long jobId;

        private NodeTemplateProvisioningOutcome(AsyncJob job) {
            super(String.class, job, 1000, () -> {
                AsyncJobVO jobVo = entityManager.findById(AsyncJobVO.class, job.getId());
                return jobVo == null || jobVo.getStatus() != JobInfo.Status.IN_PROGRESS;
            }, AsyncJob.Topics.JOB_STATE);

            jobId = job.getId();
        }

        @Override
        protected String retrieve() {
            AsyncJob job = getJob();
            if (job == null) {
                throw new CloudRuntimeException(String.format(
                        "Provisioning job [%d] not found.", jobId));
            }
            if (job.getStatus() == JobInfo.Status.FAILED) {
                throw new CloudRuntimeException(String.format("Failure in job [%d]", jobId));
            }
            return job.getResult();
        }
    }

    public void configureExecutorPool(int poolSize) {
        logger.info("Configuring TOSCA's fixed executor thread pool with [{}] threads.", poolSize);
        executorPool = Executors.newFixedThreadPool(poolSize);
    }

    public void loadToscaProfile(List<IacResourceTypeVO> iacResourceTypes) {
        logger.info("Loading TOSCA's profile with the following resource types: {}", iacResourceTypes.stream().map(IacResourceTypeVO::getName).collect(Collectors.toList()));

        toscaProfile = iacResourceTypes.stream()
                .map(resourceType -> toscaParser.parseNodeTypeDefinitionFile(resourceType.getContent()))
                .collect(Collectors.toMap(ToscaNodeType::getName, nodeType -> nodeType));
    }

    public void shutdownExecutorPool() {
        logger.info("Shutting down TOSCA's fixed executor thread pool.");
        if (executorPool != null) {
            executorPool.shutdown();
        }
    }

    protected Map<String, ToscaNodeType> getToscaProfile() {
        return Collections.unmodifiableMap(toscaProfile);
    }
}

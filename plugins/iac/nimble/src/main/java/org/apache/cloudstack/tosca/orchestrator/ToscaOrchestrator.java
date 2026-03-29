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
import com.cloud.api.ApiServer;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.UuidUtils;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.command.user.vmgroup.CreateVMGroupCmd;
import org.apache.cloudstack.api.command.user.vpc.CreateVPCCmd;
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
import org.apache.logging.log4j.ThreadContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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
        getServiceTemplateTopologicalSort(serviceTemplate).forEach((node, dependencies) -> {
            ToscaNodeTemplate nodeTemplate = serviceTemplate.getNodeTemplates().get(node);
            CompletableFuture<String> taskFuture;
            if (dependencies.isEmpty()) {
                logger.debug("Node [{}] has no dependencies. Building its provisioning task, which will be ready to be allocated for execution.", node);
                taskFuture = buildNodeProvisioningTask(nodeTemplate);
            } else {
                logger.debug("Node [{}] has [{}] dependencies. Building its provisioning task, which will only be allocated for execution when all dependencies are ready.", node, dependencies.size());
                CompletableFuture<?>[] dependenciesFutures = dependencies.stream()
                        .map((dep) -> futures.get(dep.getName())).toArray(CompletableFuture[]::new);
                taskFuture = CompletableFuture.allOf(dependenciesFutures).thenCompose(v -> {
                    logger.debug("All dependencies of the node [{}] are ready. Building its provisioning task.", node);
                    return buildNodeProvisioningTask(nodeTemplate);
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

    private CompletableFuture<String> buildNodeProvisioningTask(ToscaNodeTemplate nodeTemplate) {
        return provisionNode(nodeTemplate)
//                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(result -> {
                    logger.debug("SUCCESS [{}] [{}]", nodeTemplate.getName(), Thread.currentThread().getName());
                    return result;
                }).exceptionally(ex -> {
                    logger.debug("FAILURE [{}] [{}]", nodeTemplate.getName(), ex);
                    throw new CompletionException(ex);
                });
    }

    private CompletableFuture<String> provisionNode(ToscaNodeTemplate nodeTemplate) {
        CallContext callerContext = CallContext.current();
        String newTaskLogContextId = UuidUtils.first(UUID.randomUUID().toString());
        logger.info("Submitting new task with [logcontextid] equal to [{}] for handling the provisioning of the following node: [{}].", newTaskLogContextId, nodeTemplate.getName());
        return CompletableFuture.supplyAsync(() -> {
            ThreadContext.put("logcontextid", newTaskLogContextId);
            ManagedContextExecutor.execute(() -> {
                dispatchProvisioningCommand(nodeTemplate, callerContext);
            });
            return "res-" + nodeTemplate.getName();
        }, executorPool);
    }

    private void dispatchProvisioningCommand(ToscaNodeTemplate nodeTemplate, CallContext callContext) {
        Class<?> apiClass = apiServer.getCmdClass(nodeTemplate.getApiName());
        try {
            Object cmd = apiClass.getDeclaredConstructor().newInstance();
            if (cmd instanceof BaseAsyncCmd) {
                dispatchProvisioningAsynchronousCommand((BaseAsyncCmd) cmd, callContext);
            } else if (cmd instanceof BaseCmd) {
                dispatchProvisioningSynchronousCommand((BaseCmd) cmd, callContext);
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            logger.error("Could not instantiate the API class [{}].", apiClass.getName());
            throw new InvalidParameterValueException(String.format("Could not dispatch the provisioning task of [%s]. Please, check the availability of the API associated with it.", nodeTemplate.getName()));
        }
    }

    private void dispatchProvisioningAsynchronousCommand(BaseAsyncCmd asyncCmd, CallContext callContext) {
        Map<String, String> params = new HashMap<>(Map.of(
                "zoneid", "309ea14d-ce26-44eb-ac05-53106b0ccb17",
                "name", "vpc-" + new Random().nextInt(100000),
                "vpcofferingid", "3e70fd9b-bc5a-4d4b-89f1-40dc756e8058",
                "cidr", "10.0.0.0/16",
                "ctxUserId", String.valueOf(callContext.getCallingUserId()),
                "ctxAccountId", String.valueOf(callContext.getCallingAccountId())
        ));
        CreateVPCCmd cmd = new CreateVPCCmd();
        cmd = ComponentContext.inject(cmd);
        try {
            CallContext.register(callContext, null);
            apiDispatcher.dispatchCreateCmd(cmd, params);
            params.put("ctxStartEventId", "1");
            Long objectId = ObjectUtils.defaultIfNull(cmd.getEntityId(), cmd.getApiResourceId());
            params.put("id", objectId.toString());
            AsyncJobVO job = new AsyncJobVO("", callContext.getCallingUserId(), callContext.getCallingAccountId(), CreateVPCCmd.class.getName(),
                    ApiGsonHelper.getBuilder().create().toJson(params), objectId,
                    cmd.getApiResourceType() != null ? cmd.getApiResourceType().toString() : null,
                    null);
            job.setDispatcher(asyncJobDispatcher.getName());
            long jobId = asyncJobManager.submitAsyncJob(job);
            AsyncJobExecutionContext executionContext = AsyncJobExecutionContext.getCurrentExecutionContext();
            executionContext.joinJob(jobId);
            Outcome<String> outcome = new NodeTemplateProvisioningOutcome(job);
            String jobResult = outcome.get();
            logger.info("Provisioning outcome: {}", jobResult);
            asyncJobManager.expungeAsyncJob((AsyncJobVO) executionContext.getJob());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void dispatchProvisioningSynchronousCommand(BaseCmd syncCmd, CallContext callContext) {
        logger.debug("Constructing provisioning command");
        CallContext.register(callContext, null);
        CreateVMGroupCmd cmd = new CreateVMGroupCmd();
        cmd = ComponentContext.inject(cmd);
        Map<String, String> params = Map.of(
                "domainid", "1",
                "account", "admin",
                "name", "instance-group-" + new Random().nextInt(100000)
        );
        try {
            apiDispatcher.dispatch(cmd, params, false);
            logger.info(cmd.getResponseObject());
        } catch (Exception e) {

        } finally {
            CallContext.unregister();
        }
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

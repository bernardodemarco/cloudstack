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
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.UuidUtils;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.api.command.user.vmgroup.CreateVMGroupCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeVO;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;
import org.apache.cloudstack.tosca.parser.ToscaParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import javax.inject.Inject;
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

    private Map<String, ToscaNodeType> toscaProfile;

    private ExecutorService executorPool;

    public void deployIacTemplate(String iacTemplateContent) {
        logger.debug("Parsing service template");
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(iacTemplateContent, toscaProfile, null);
        Map<String, CompletableFuture<String>> provisioningTasksFutures = createProvisioningTasksFutures(serviceTemplate);
        CompletableFuture<Void> serviceTemplateFeature = CompletableFuture.allOf(provisioningTasksFutures.values().toArray(new CompletableFuture[0]));
        serviceTemplateFeature.join();
        logger.debug("After serviceTemplateFeature.join()");
    }

    private Map<String, CompletableFuture<String>> createProvisioningTasksFutures(ToscaServiceTemplate serviceTemplate) {
        logger.debug("Creating provisioning tasks futures");
        Map<String, CompletableFuture<String>> futures = new HashMap<>();
        getServiceTemplateTopologicalSort(serviceTemplate).forEach((node, dependencies) -> {
            ToscaNodeTemplate nodeTemplate = serviceTemplate.getNodeTemplates().get(node);
            CompletableFuture<String> taskFuture;
            if (dependencies.isEmpty()) {
                logger.debug("Node [{}] has no dependencies. Building its provisioning task.", node);
                taskFuture = buildNodeProvisioningTask(nodeTemplate);
            } else {
                CompletableFuture<?>[] dependenciesFutures = dependencies.stream()
                        .map((dep) -> futures.get(dep.getName())).toArray(CompletableFuture[]::new);
                taskFuture = CompletableFuture.allOf(dependenciesFutures).thenCompose(v -> {
                    logger.debug("Thread: [{}]", Thread.currentThread().getName());
                    logger.debug("All dependencies of the node [{}] are ready.", node);
                    logger.debug("Here you'll be able to resolve the unresolved properties by get property and get attribute");
                    return buildNodeProvisioningTask(nodeTemplate);
                });
            }

            futures.put(node, taskFuture);
        });

        logger.debug("All provisioning tasks futures have been built successfully.");
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
                throw new InvalidParameterValueException("Cycle detected in node dependency graph - this should have been caught during template validation");
            }

            if (!visitedNodes.contains(dependency.getName())) {
                depthFirstSearch(topologicalSort, dependency.getName(), graph, branchAncestors, visitedNodes);
            }
        }

        branchAncestors.remove(node);
        topologicalSort.put(node, graph.getOrDefault(node, Collections.emptySet()));
    }

    private CompletableFuture<String> buildNodeProvisioningTask(ToscaNodeTemplate nodeTemplate) {
        logger.debug("Inside build node provisioning task for node: " + nodeTemplate.getName());
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
        String currentLogContextId = ThreadContext.get("logcontextid");
        String newTaskLogContextId = UuidUtils.first(UUID.randomUUID().toString());
        logger.info("Submitting new task with [logcontextid] equal to [{}] for handling the provisioning of the following node: [{}].", newTaskLogContextId, nodeTemplate.getName());
        return CompletableFuture.supplyAsync(() -> {
            ThreadContext.put("logcontextid", newTaskLogContextId);
            logger.debug("Executing thread {} for {}", Thread.currentThread().getName(), nodeTemplate.getName());

            Random random = new Random();
            int randomInt = random.nextInt((4000 - 500) + 1) + 500;
            logger.debug("sleeping for randomInt: [{}] ms", randomInt);
            try {
                dispatchProvisioningCommand(callerContext, randomInt);
                Thread.sleep(randomInt);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            logger.debug("Here you'll be able to populate the attributes");
//            ThreadContext.put("logcontextid", currentLogContextId);
            return "res-" + nodeTemplate.getName();
        }, executorPool);
    }

    private void dispatchProvisioningCommand(CallContext ctx, int randomInt) {
        logger.debug("Constructing provisioning command");
        CallContext.register(ctx, null);
        CreateVMGroupCmd cmd = new CreateVMGroupCmd();
        cmd = ComponentContext.inject(cmd);
        Map<String, String> params = Map.of(
                "domainid", "1",
                "account", "admin",
                "name", "instance-group-" + randomInt
        );
        try {
            apiDispatcher.dispatch(cmd, params, false);
            logger.info(cmd.getResponseObject());
        } catch (Exception e) {

        } finally {
            CallContext.unregister();
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

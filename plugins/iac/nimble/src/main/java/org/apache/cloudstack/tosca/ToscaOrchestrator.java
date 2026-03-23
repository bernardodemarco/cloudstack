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
package org.apache.cloudstack.tosca;

import com.cloud.exception.InvalidParameterValueException;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeVO;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;
import org.apache.cloudstack.tosca.parser.ToscaParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    private Map<String, ToscaNodeType> toscaProfile;

    private ExecutorService executorPool;

    public void deployIacTemplate(String iacTemplateContent) {
        logger.debug("Parsing service template");
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(iacTemplateContent, toscaProfile, null);
        Map<ToscaNodeTemplate, CompletableFuture<String>> provisioningTasksFutures = createProvisioningTasksFutures(serviceTemplate);
        CompletableFuture<Void> serviceTemplateFeature = CompletableFuture.allOf(provisioningTasksFutures.values().toArray(new CompletableFuture[0]));
        serviceTemplateFeature.join();
    }

    private Map<ToscaNodeTemplate, CompletableFuture<String>> createProvisioningTasksFutures(ToscaServiceTemplate serviceTemplate) {
        logger.debug("Creating provisioning tasks futures");
        Map<ToscaNodeTemplate, CompletableFuture<String>> futures = new HashMap<>();
        Map<String, Set<ToscaNodeTemplate>> dependencyGraph = serviceTemplate.getDependencyGraph();

        List<String> nodesWithoutDependencies = dependencyGraph.keySet().stream().filter(node -> dependencyGraph.get(node).isEmpty()).collect(Collectors.toList());
        logger.debug("Nodes without dependencies: " + nodesWithoutDependencies);
        nodesWithoutDependencies.forEach(node -> {
            ToscaNodeTemplate nodeTemplate = serviceTemplate.getNodeTemplates().get(node);
            CompletableFuture<String> taskFuture = buildNodeProvisioningTask(nodeTemplate);
            futures.put(nodeTemplate, taskFuture);
        });
        logger.debug("Built all the features for the nodes without dependencies.");

        logger.debug("Service template size: " + serviceTemplate.getNodeTemplates().size());
        while (futures.size() < serviceTemplate.getNodeTemplates().size()) {
            logger.debug("Futures size: " + futures.size());
            List<ToscaNodeTemplate> nodesWhoseDependenciesAlreadyHaveFutures = dependencyGraph.keySet().stream()
                    .filter(node -> !dependencyGraph.get(node).isEmpty() && dependencyGraph.get(node).stream().allMatch(futures::containsKey))
                    .map(node -> serviceTemplate.getNodeTemplates().get(node))
                    .collect(Collectors.toList());

            for (ToscaNodeTemplate node : nodesWhoseDependenciesAlreadyHaveFutures) {
                CompletableFuture<?>[] dependencies = dependencyGraph.get(node.getName()).stream().map(futures::get).toArray(CompletableFuture[]::new);
                CompletableFuture<String> taskFuture = CompletableFuture.allOf(dependencies).thenCompose(v -> {
                    logger.debug("All dependencies of the node [" + node.getName() + "] are ready.");
                    logger.debug("Here you'll be able to resolve the unresolved properties by get property and get attribute");
                    return buildNodeProvisioningTask(node);
                });

                futures.put(node, taskFuture);
            }
        }

        return futures;
    }

    // O(|V|+|E|)
    private List<String> getServiceTemplateTopologicalSort(ToscaServiceTemplate serviceTemplate) {
        Set<String> visitedNodes = new HashSet<>();
        Set<String> branchAncestors = new HashSet<>();
        List<String> topologicalSort = new ArrayList<>();
        for (ToscaNodeTemplate node : serviceTemplate.getNodeTemplates().values()) {
            if (!visitedNodes.contains(node.getName())) {
                depthFirstSearch(topologicalSort, node.getName(), serviceTemplate.getDependencyGraph(), branchAncestors, visitedNodes);
            }
        }
        return topologicalSort;
    }

    private void depthFirstSearch(List<String> topologicalSort, String node, Map<String, Set<ToscaNodeTemplate>> graph, Set<String> branchAncestors, Set<String> visitedNodes) {
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
        topologicalSort.add(node);
    }

    private CompletableFuture<String> buildNodeProvisioningTask(ToscaNodeTemplate nodeTemplate) {
        logger.debug("Inside build node provisioning task for node: " + nodeTemplate.getName());
        return provisionNode(nodeTemplate)
//                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(result -> {
                    System.out.println("✔ SUCCESS " + nodeTemplate.getName());
                    return result;
                }).exceptionally(ex -> {
                    System.out.println("✖ FAILURE " + nodeTemplate.getName() + " → " + ex);
                    throw new CompletionException(ex);
                });
    }

    private CompletableFuture<String> provisionNode(ToscaNodeTemplate nodeTemplate) {
        return CompletableFuture.supplyAsync(() -> {

            logger.debug("Submitting async job for " + nodeTemplate.getName() + " on " + Thread.currentThread().getName());

            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            logger.debug("Here you'll be able to populate the attributes");
            return "res-" + nodeTemplate.getName();

        }, executorPool);
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

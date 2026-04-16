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
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseAsyncCreateCmd;
import org.apache.cloudstack.api.BaseCmd;
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
import org.apache.cloudstack.tosca.functions.ToscaFunction;
import org.apache.cloudstack.tosca.model.ToscaInputDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaProperty;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;
import org.apache.cloudstack.tosca.parser.ToscaConstants;
import org.apache.cloudstack.tosca.parser.ToscaParser;
import org.apache.cloudstack.tosca.parser.ToscaYamlHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
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

    public void deployIacTemplate(String iacTemplateContent, Map<String, String> inputs) {
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(iacTemplateContent, toscaProfile, null);
        resolveServiceTemplateInputs(serviceTemplate, inputs);
        Map<String, CompletableFuture<String>> provisioningTasksFutures = createProvisioningTasksFutures(serviceTemplate);
        logger.debug("Awaiting for all the provisioning tasks of the node template to complete.");
        CompletableFuture<Void> serviceTemplateFeature = CompletableFuture.allOf(provisioningTasksFutures.values().toArray(new CompletableFuture[0]));
        serviceTemplateFeature.join();
        logger.info("All provisioning tasks have completed successfully.");
    }

    /**
     * Resolves the unresolved properties of a node template by the <code>$get_input</code> TOSCA function.
     * @param serviceTemplate The service template whose node templates properties will be resolved.
     * @param inputs The user provided inputs.
     * @throws InvalidParameterValueException If the user-provided inputs do not match the service template's inputs or are not accepted values by the service template.
     */
    protected void resolveServiceTemplateInputs(ToscaServiceTemplate serviceTemplate, Map<String, String> inputs) {
        Set<String> unknownInputs = inputs.keySet().stream()
                .filter(input -> !serviceTemplate.getInputs().containsKey(input)).collect(Collectors.toSet());
        if (CollectionUtils.isNotEmpty(unknownInputs)) {
            throw new InvalidParameterValueException(String.format("The following inputs have been specified but are not accepted by the service template: %s.", unknownInputs));
        }

        if (MapUtils.isEmpty(serviceTemplate.getInputs())) {
            logger.debug("The service template has no inputs. Skipping the input resolution workflow.");
            return;
        }

        serviceTemplate.getUnresolvedPropertiesByGetInput().forEach((nodeTemplate, unresolvedProperties) -> {
            unresolvedProperties.forEach(property -> {
                handleGetInputFunctionCall(nodeTemplate, property, serviceTemplate, inputs);
            });
        });
    }

    private void handleGetInputFunctionCall(String nodeTemplateName, ToscaProperty property, ToscaServiceTemplate serviceTemplate, Map<String, String> inputs) {
        String propertyName = property.getDefinition().getName();
        logger.debug("Resolving the [{}] function call triggered by the [{}] property of the [{}] node template.", ToscaConstants.GET_INPUT_FUNCTION, propertyName, nodeTemplateName);

        Object evaluatedValue = getPropertyEvaluatedValue(property.getRawValue(), serviceTemplate, nodeTemplateName, inputs);
        ToscaFunction.ToscaBooleanFunction propertyValidationFunction = property.getDefinition().getValidation();
        if (propertyValidationFunction != null && property.getDefinition().getType().getKind() == ToscaTypeDefinition.Kind.PRIMITIVE && !propertyValidationFunction.evaluate(evaluatedValue)) {
            throw new InvalidParameterValueException(String.format("The value [%s] does not satisfy the validation rules of the property [%s] of the node template [%s].", evaluatedValue, propertyName, nodeTemplateName));
        }

        logger.debug("Resolving the evaluated value of the property [{}] of the node template [{}] to be equal to: [{}].", propertyName, nodeTemplateName, evaluatedValue);
        property.setEvaluatedValue(evaluatedValue);
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
                    executeGetAttributeAndGetPropertyFunctionCalls(nodeTemplate, serviceTemplate);
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
            populateNodeTemplateAttributes(nodeTemplate, provisioningResult);
        } catch (Exception e) {
            logger.error("Could not instantiate the API class [{}]: {}.", apiClass.getName(), e.getMessage());
            throw new InvalidParameterValueException(String.format("Could not dispatch the provisioning task of [%s]. Please, check the availability of the API associated with it.", nodeTemplate.getName()));
        }
    }

    private Map<String, Object> dispatchProvisioningSynchronousCommand(BaseCmd syncCmd, Map<String, String> apiParams) throws Exception {
        logger.info("Dispatching the provisioning synchronous command [{}] with the following parameters {}.", syncCmd.getClass().getName(), apiParams);
        syncCmd = ComponentContext.inject(syncCmd);
        apiDispatcher.dispatch(syncCmd, apiParams, false);
        return ApiSerializerHelper.fromSerializedStringToMap(ApiSerializerHelper.toSerializedString(syncCmd.getResponseObject()));
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

    /**
     * Populates the attributes of a node template based on the result of its provisioning task.
     * Each attribute declared in the node template's type will be searched in the <code>potentialAttributes</code>
     * map. If its corresponding value is found, it will be used to populate the node template's attribute.
     * @param nodeTemplate The node template whose attributes will be resolved.
     * @param potentialAttributes The return of the node template's provisioning task from which the values of the attributes will be retrieved.
     */
    protected void populateNodeTemplateAttributes(ToscaNodeTemplate nodeTemplate, Map<String, Object> potentialAttributes) {
        ToscaNodeType nodeType = nodeTemplate.getType();
        if (nodeType.getAttributes().isEmpty()) {
            logger.debug("Node template [{}] has no attributes to be populated.", nodeTemplate.getName());
            return;
        }

        logger.debug("Populating node template [{}] attributes.", nodeTemplate.getName());
        nodeType.getAttributes().forEach((name, definition) -> {
            String apiResponseAttribute = definition.getApiResponseAttribute();
            Object value = potentialAttributes.get(apiResponseAttribute);
            if (value == null) {
                logger.debug("Not populating attribute [{}] of node template [{}], because it is not available in the API response.", name, nodeTemplate.getName());
            } else {
                logger.debug("Populating attribute [{}] of node template [{}] with value [{}].", name, nodeTemplate.getName(), value);
                nodeTemplate.addAttribute(name, value);
            }
        });
    }

    /**
     * Executes the <code>$get_attribute</code> and <code>$get_property</code> TOSCA functions called by a node template.
     * @param nodeTemplate The node template from which the functions are called.
     * @param serviceTemplate The TOSCA service template the node template belongs to.
     * @throws InvalidParameterValueException When the return value of the <code>$get_attribute</code> and <code>$get_property</code> function calls is null
     * or when the return value does not pass the validation function.
     */
    protected void executeGetAttributeAndGetPropertyFunctionCalls(ToscaNodeTemplate nodeTemplate, ToscaServiceTemplate serviceTemplate) {
        Set<ToscaProperty> unresolvedProperties = new HashSet<>(nodeTemplate.getUnresolvedPropertiesByGetProperty());
        unresolvedProperties.addAll(nodeTemplate.getUnresolvedPropertiesByGetAttribute());
        if (CollectionUtils.isEmpty(unresolvedProperties)) {
            logger.debug("Node template [{}] has no unresolved properties by the [{}] and [{}] TOSCA functions.", nodeTemplate.getName(), ToscaConstants.GET_ATTRIBUTE_FUNCTION, ToscaConstants.GET_PROPERTY_FUNCTION);
            return;
        }

        logger.info("Handling the [{}] and [{}] TOSCA function calls performed by the [{}] node template.", ToscaConstants.GET_ATTRIBUTE_FUNCTION, ToscaConstants.GET_PROPERTY_FUNCTION, nodeTemplate.getName());
        for (ToscaProperty property : unresolvedProperties) {
            Object evaluatedValue = getPropertyEvaluatedValue(property.getRawValue(), serviceTemplate, nodeTemplate.getName(), null);

            if (property.getDefinition().getValidation() != null && property.getDefinition().getType().getKind() == ToscaTypeDefinition.Kind.PRIMITIVE) {
                logger.debug("The property [{}] has a validation clause. Executing it.", property.getDefinition().getName());
                boolean validationResult = property.getDefinition().getValidation().evaluate(evaluatedValue);
                if (!validationResult) {
                    logger.error("The value of the property [{}] of node template [{}] is not valid. Aborting IaC template deployment.", property.getDefinition().getName(), nodeTemplate.getName());
                    throw new InvalidParameterValueException(String.format("The value of the property [%s] of node template [%s] is not valid. Please, check the value and try again.", property.getDefinition().getName(), nodeTemplate.getName()));
                }
            }

            logger.debug("Property [{}] of node template [{}] resolved to [{}].", property.getDefinition().getName(), nodeTemplate.getName(), evaluatedValue);
            property.setEvaluatedValue(evaluatedValue);
        }
    }

    private Object getPropertyEvaluatedValue(Object rawValue, ToscaServiceTemplate serviceTemplate, String nodeTemplateName, Map<String, String> rawInputs) {
        if (rawValue instanceof List) {
            List<Object> evaluatedValue = new ArrayList<>();
            for (Object item : (List<?>) rawValue) {
                evaluatedValue.add(getPropertyEvaluatedValue(item, serviceTemplate, nodeTemplateName, rawInputs));
            }
            return evaluatedValue;
        }

        if (rawValue instanceof Map) {
            Map<String, Object> rawValueAsMap = ToscaYamlHelper.asMap(rawValue);
            String functionName = rawValueAsMap.keySet().iterator().next();
            boolean isToscaFunction = rawValueAsMap.size() == 1 && ToscaConstants.GETTER_FUNCTION_KEYS.contains(functionName);
            if (isToscaFunction) {
                if (ToscaConstants.GET_INPUT_FUNCTION.equals(functionName)) {
                    return resolveGetInputFunctionCall(rawValueAsMap, nodeTemplateName, serviceTemplate, rawInputs);
                }
                return resolveGetAttributeAndGetPropertyFunctionCall(rawValueAsMap, nodeTemplateName, serviceTemplate);
            }

            Map<String, Object> evaluatedValue = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rawValueAsMap.entrySet()) {
                evaluatedValue.put(entry.getKey(), getPropertyEvaluatedValue(entry.getValue(), serviceTemplate, nodeTemplateName, rawInputs));
            }
            return evaluatedValue;
        }

        return rawValue;
    }

    private Object resolveGetInputFunctionCall(Map<String, Object> functionCall, String nodeTemplateName, ToscaServiceTemplate serviceTemplate, Map<String, String> inputValues) {
        String inputName = ToscaYamlHelper.asString(functionCall.get(ToscaConstants.GET_INPUT_FUNCTION));
        ToscaInputDefinition inputDefinition = serviceTemplate.getInputs().get(inputName);
        Object inputValue = inputDefinition.getType().convertPrimitiveTypeFromString(inputValues.get(inputName));

        ToscaFunction.ToscaBooleanFunction inputValidationFunction = inputDefinition.getValidation();
        if (inputValue != null && inputValidationFunction != null && !inputValidationFunction.evaluate(inputValue)) {
            throw new InvalidParameterValueException(String.format("The input [%s] declared in the node template [%s] is invalid. The value [%s] does not satisfy the validation rules.", inputName, nodeTemplateName, inputValue));
        }

        if (inputValue == null) {
            logger.debug("The input [{}] was not provided. Checking if it has a default value.", inputName);
            if (inputDefinition.getDefaultValue() == null) {
                throw new InvalidParameterValueException(String.format("The input [%s] declared in the node template [%s] is required but it was not provided and it does not have a default value.", inputName, nodeTemplateName));
            }
            inputValue = inputDefinition.getDefaultValue();
        }
        return inputValue;
    }

    private Object resolveGetAttributeAndGetPropertyFunctionCall(Map<String, Object> functionCall, String nodeTemplateName, ToscaServiceTemplate serviceTemplate) {
        String toscaFunction = functionCall.keySet().iterator().next();
        List<?> args = ToscaYamlHelper.asList(functionCall.get(toscaFunction));
        String targetNodeName = ToscaYamlHelper.asString(args.get(0));
        String targetField = ToscaYamlHelper.asString(args.get(1));

        ToscaNodeTemplate targetNode = serviceTemplate.getNodeTemplates().get(targetNodeName);
        Object functionCallResult = ToscaConstants.GET_PROPERTY_FUNCTION.equals(toscaFunction) ?
                targetNode.getProperty(targetField).getEvaluatedValue() : targetNode.getAttribute(targetField);

        if (functionCallResult == null) {
            throw new InvalidParameterValueException(String.format("The field [%s] of the target node [%s] referenced by the TOSCA function [%s] has not been defined. Unable to deploy [%s].", targetField, targetNode.getName(), toscaFunction, nodeTemplateName));
        }
        return functionCallResult;
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

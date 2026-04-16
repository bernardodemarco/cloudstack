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
package org.apache.cloudstack.tosca.parser;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.Account;
import com.cloud.utils.Pair;
import org.apache.cloudstack.tosca.functions.ToscaFunction;
import org.apache.cloudstack.tosca.model.ToscaFieldDefinition;
import org.apache.cloudstack.tosca.model.ToscaInputDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaProperty;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToscaServiceTemplateParser {
    private final Logger logger = LogManager.getLogger(ToscaServiceTemplateParser.class);

    private final ToscaFieldParser toscaFieldParser;

    @Inject
    public ToscaServiceTemplateParser(ToscaFieldParser toscaFieldParser) {
        this.toscaFieldParser = toscaFieldParser;
    }

    public ToscaServiceTemplate parseServiceTemplate(String content, Map<String, ToscaNodeType> toscaProfile, Account caller) {
        Map<String, Object> rawToscaTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(content));
        logger.info("Parsing TOSCA service template.");
        ToscaServiceTemplateParsingContext context = new ToscaServiceTemplateParsingContext(toscaProfile);

        checkRootServiceTemplateYamlKeys(rawToscaTemplate, context);
        Map<String, Object> rawServiceTemplate = ToscaYamlHelper.asMap(rawToscaTemplate.get(ToscaConstants.SERVICE_TEMPLATE));
        Map<String, ToscaInputDefinition> inputs = parseInputs(ToscaYamlHelper.asMap(rawServiceTemplate.get(ToscaConstants.INPUTS)), context);
        Map<String, ToscaNodeTemplate> nodeTemplates = parseNodeTemplates(ToscaYamlHelper.asMap(rawServiceTemplate.get(ToscaConstants.NODE_TEMPLATES)), context);

        verifyToscaGetterFunctionCalls(nodeTemplates, ToscaConstants.GET_PROPERTY_FUNCTION, context);
        verifyToscaGetterFunctionCalls(nodeTemplates, ToscaConstants.GET_ATTRIBUTE_FUNCTION, context);
        Map<String, Set<ToscaNodeTemplate>> dependencyGraph = buildDependencyGraph(nodeTemplates, context);

        if (context.hasErrors()) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }

        logger.info("The TOSCA service template has been successfully parsed and validated.");
        return new ToscaServiceTemplate(nodeTemplates, dependencyGraph, inputs, context.getUnresolvedPropertiesByGetInput());
    }

    /**
     * Checks whether all the YAML keys present in the root and service_template levels of
     * TOSCA service templates are known and whether all the required ones were provided.
     * @param template The TOSCA service template to be validated.
     * @param context The service template parsing context ({@link ToscaServiceTemplateParsingContext}) for error handling purposes.
     * @throws InvalidParameterValueException When unknown keys are present or required keys are missing.
     */
    private void checkRootServiceTemplateYamlKeys(Map<String, Object> template, ToscaServiceTemplateParsingContext context) {
        logger.debug("Checking root service template YAML keys: {}.", template.keySet());
        validateKnownToscaKeys(template, Set.of(ToscaConstants.TOSCA_DEFINITIONS_VERSION, ToscaConstants.DESCRIPTION, ToscaConstants.SERVICE_TEMPLATE), "Root level", context);
        boolean missingRootRequiredKeys = checkMissingRequiredToscaKeys(template, Set.of(ToscaConstants.SERVICE_TEMPLATE), "Root level", context);
        if (missingRootRequiredKeys) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }

        Map<String, Object> serviceTemplateSection = ToscaYamlHelper.asMap(template.get(ToscaConstants.SERVICE_TEMPLATE));
        logger.debug("Checking service template YAML keys: {}.", serviceTemplateSection.keySet());
        validateKnownToscaKeys(serviceTemplateSection, Set.of(ToscaConstants.NODE_TEMPLATES, ToscaConstants.INPUTS, ToscaConstants.SERVICE_TEMPLATE), "Service template level", context);
        boolean missingServiceTemplateRequiredKeys = checkMissingRequiredToscaKeys(serviceTemplateSection, Set.of(ToscaConstants.NODE_TEMPLATES), "Service template level", context);
        if (missingServiceTemplateRequiredKeys) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }
    }

    /**
     * Parse service template inputs.
     * @param inputs The map of inputs present in the service template.
     * @param context The service template parsing context ({@link ToscaServiceTemplateParsingContext}).
     * @return a map of {@link ToscaInputDefinition} objects representing the parsed inputs. Inputs with inconsistencies in their definitions
     * are not added to the returned map. Instead, the corresponding inconsistency is added to the error messages of the service template parsing
     * context ({@link ToscaServiceTemplateParsingContext}) to be later thrown along with other eventual errors.
     */
    protected Map<String, ToscaInputDefinition> parseInputs(Map<String, Object> inputs, ToscaServiceTemplateParsingContext context) {
        logger.debug("Parsing the following inputs: {}.", inputs.keySet());
        Map<String, ToscaInputDefinition> inputDefinitions = new HashMap<>();
        for (Map.Entry<String, Object> input : inputs.entrySet()) {
            ToscaInputDefinition inputDefinition = parseInput(input.getKey(), ToscaYamlHelper.asMap(input.getValue()), context);
            if (inputDefinition != null) {
                logger.info("Successfully parsed the input [{}].", input.getKey());
                inputDefinitions.put(inputDefinition.getName(), inputDefinition);
            } else {
                logger.info("The input [{}] is invalid. Skipping it.", input.getKey());
            }
        }

        logger.debug("The following inputs have been successfully parsed: {}. Adding them to the parsing context.", inputDefinitions.keySet());
        context.setInputs(inputDefinitions);
        return inputDefinitions;
    }

    /**
     * Parses a single input definition.
     * @param name The name of the input.
     * @param body The input's body, i.e., the map representing its properties.
     * @param context The service template parsing context ({@link ToscaServiceTemplateParsingContext}).
     * @return a {@link ToscaInputDefinition} object representing the parsed input, or {@code null} if the input is invalid. Invalid inputs
     * are the ones that have an invalid type, a default value incompatible with the input type, or a validation function that is not a supported TOSCA boolean function.
     */
    private ToscaInputDefinition parseInput(String name, Map<String, Object> body, ToscaServiceTemplateParsingContext context) {
        validateKnownToscaKeys(body,
                Set.of(ToscaConstants.DESCRIPTION, ToscaConstants.TYPE, ToscaConstants.VALIDATION, ToscaConstants.DEFAULT_VALUE),
                ToscaConstants.INPUTS, context);

        ToscaTypeDefinition type = toscaFieldParser.parseType(body, null);
        if (type == null || type.getKind() != ToscaTypeDefinition.Kind.PRIMITIVE) {
            logger.debug("The type of the input [{}] is not specified or it is not supported. Skipping it.", name);
            context.addError(String.format("The type of the input [%s] was not specified or it is not supported (only TOSCA primitive types are currently supported).", name), ToscaConstants.INPUTS);
            return null;
        }

        Object defaultValue = body.get(ToscaConstants.DEFAULT_VALUE);
        if (defaultValue != null && !type.isCompatibleWith(defaultValue)) {
            logger.debug("The default value [{}] of the input [{}] is not compatible with the input type [{}]. Skipping it.", defaultValue, name, type);
            context.addError(String.format("The provided default value [%s] for the input [%s] is not compatible with the input type [%s].", defaultValue, name, type), ToscaConstants.INPUTS);
            return null;
        }

        Map<String, Object> rawValidation = ToscaYamlHelper.asMap(body.get(ToscaConstants.VALIDATION));
        ToscaFunction.ToscaBooleanFunction validation = toscaFieldParser.parseToscaBooleanFunction(rawValidation);
        if (MapUtils.isNotEmpty(rawValidation) && validation == null) {
            logger.debug("The validation function of the input [{}] is not valid. Skipping it.", name);
            context.addError(String.format("The validation function of the input [%s] is not valid.", name), ToscaConstants.INPUTS);
            return null;
        }

        String description = ToscaYamlHelper.asString(body.get(ToscaConstants.DESCRIPTION));
        return new ToscaInputDefinition(name, description, type, defaultValue, validation);
    }

    protected Map<String, ToscaNodeTemplate> parseNodeTemplates(Map<String, Object> nodeTemplates, ToscaServiceTemplateParsingContext context) {
        logger.debug("Parsing the following node templates: {}.", nodeTemplates.keySet());
        Map<String, ToscaNodeTemplate> nodeTemplateDefinitions = new HashMap<>();
        for (Map.Entry<String, Object> nodeTemplate : nodeTemplates.entrySet()) {
            ToscaNodeTemplate toscaNodeTemplate = parseNodeTemplate(nodeTemplate.getKey(), ToscaYamlHelper.asMap(nodeTemplate.getValue()), context);
            if (toscaNodeTemplate != null) {
                logger.info("Successfully parsed the node template [{}].", nodeTemplate.getKey());
                nodeTemplateDefinitions.put(toscaNodeTemplate.getName(), toscaNodeTemplate);
            } else {
                logger.info("The node template [{}] is invalid. Skipping it.", nodeTemplate.getKey());
            }
        }

        logger.debug("The following node templates have been successfully parsed: {}.", nodeTemplateDefinitions.keySet());
        return nodeTemplateDefinitions;
    }

    private ToscaNodeTemplate parseNodeTemplate(String nodeTemplateName, Map<String, Object> body, ToscaServiceTemplateParsingContext context) {
        validateKnownToscaKeys(body, Set.of(ToscaConstants.PROPERTIES, ToscaConstants.TYPE, ToscaConstants.REQUIREMENTS), "node templates section", context);
        boolean missingRequiredNodeTemplateKeys = checkMissingRequiredToscaKeys(body, Set.of(ToscaConstants.PROPERTIES, ToscaConstants.TYPE), "node templates section", context);
        if (missingRequiredNodeTemplateKeys) {
            logger.debug("The node template [{}] is missing required keys. Skipping it.", nodeTemplateName);
            return null;
        }

        String nodeTypeName = ToscaYamlHelper.asString(body.get(ToscaConstants.TYPE));
        if (nodeTypeName == null || !context.getProfile().containsKey(nodeTypeName)) {
            logger.debug("The node type [{}] of the node template [{}] is not valid. Skipping it.", nodeTypeName, nodeTemplateName);
            context.addError(String.format("The node type [%s] is not valid.", nodeTypeName), ToscaConstants.NODE_TEMPLATES);
            return null;
        }
        ToscaNodeType nodeType = context.getProfile().get(nodeTypeName);

        parseNodeTemplateRequirements(nodeTemplateName, ToscaYamlHelper.asList(body.get(ToscaConstants.REQUIREMENTS)), context);
        Map<String, ToscaProperty> properties = parseNodeTemplateProperties(nodeTemplateName, ToscaYamlHelper.asMap(body.get(ToscaConstants.PROPERTIES)), nodeType, context);
        if (properties == null) {
            logger.info("The node template [{}] is missing required properties. Skipping it.", nodeTemplateName);
            return null;
        }

        return new ToscaNodeTemplate(nodeTemplateName, nodeType, properties);
    }

    private Map<String, ToscaProperty> parseNodeTemplateProperties(String nodeTemplateName, Map<String, Object> properties, ToscaNodeType nodeType, ToscaServiceTemplateParsingContext context) {
        validateKnownToscaKeys(properties, nodeType.getProperties().keySet(), nodeTemplateName + " declaration", context);
        Set<String> nodeTypeRequiredProperties = nodeType.getRequiredPropertyNames();
        boolean missingRequiredProperties = checkMissingRequiredToscaKeys(properties, nodeTypeRequiredProperties, nodeTemplateName + " declaration", context);
        if (missingRequiredProperties) {
            logger.debug("The node template [{}] is missing required properties. Skipping it.", nodeTemplateName);
            return null;
        }

        Map<String, ToscaProperty> propertyDefinitions = new HashMap<>();
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            String propertyName = property.getKey();
            if (!nodeType.getProperties().containsKey(propertyName)) {
                logger.debug("The property [{}] does not belong to the type definition of the node template [{}]. Skipping it.", propertyName, nodeTemplateName);
                continue;
            }
            ToscaProperty toscaProperty = parseProperty(propertyName, property.getValue(), nodeType.getProperties().get(propertyName), nodeTemplateName, context);
            if (toscaProperty != null) {
                logger.info("Successfully parsed the property [{}] of the node template [{}].", propertyName, nodeTemplateName);
                propertyDefinitions.put(propertyName, toscaProperty);
            } else {
                logger.info("The property [{}] of the node template [{}] is invalid. Skipping it.", propertyName, nodeTemplateName);
            }
        }

        logger.debug("The following properties have been successfully parsed: {}.", propertyDefinitions.keySet());
        return propertyDefinitions;
    }

    private ToscaProperty parseProperty(String propertyName, Object propertyBody, ToscaPropertyDefinition propertyDefinition, String nodeTemplateName, ToscaServiceTemplateParsingContext context) {
        logger.debug("Parsing the property [{}] of the node template [{}].", propertyName, nodeTemplateName);
        ToscaTypeDefinition type = propertyDefinition.getType();
        if (!type.isCompatibleWith(propertyBody)) {
            context.addError(String.format("The provided value [%s] for the property [%s] of the [%s] node template is not compatible with the property type [%s].", propertyBody, propertyName, nodeTemplateName, propertyDefinition.getType()), nodeTemplateName);
            return null;
        }

        ToscaProperty property = new ToscaProperty(propertyDefinition, propertyBody, null);
        Pair<Map<String, Set<ToscaGetterFunctionCallContext>>, Boolean> functionCallsResult = getAllToscaFunctionCalls(property, property.getRawValue(), type, context);
        Boolean success = functionCallsResult.second();
        if (!success) {
            return null;
        }

        Map<String, Set<ToscaGetterFunctionCallContext>> functionCalls = functionCallsResult.first();
        ToscaFunction.ToscaBooleanFunction validationFunction = propertyDefinition.getValidation();
        if (MapUtils.isEmpty(functionCalls) && validationFunction != null && !validationFunction.evaluate(propertyBody)) {
            context.addError(String.format("The provided value [%s] for the property [%s] of the [%s] node template is not valid according to the property validation function.", propertyBody, propertyName, nodeTemplateName), nodeTemplateName);
            return null;
        }

        if (MapUtils.isEmpty(functionCalls)) {
            property.setEvaluatedValue(propertyBody);
            return property;
        }

        if (functionCalls.containsKey(ToscaConstants.GET_INPUT_FUNCTION)) {
            context.addGetInputFunctionCalls(nodeTemplateName, functionCalls.get(ToscaConstants.GET_INPUT_FUNCTION));
        }

        if (functionCalls.containsKey(ToscaConstants.GET_ATTRIBUTE_FUNCTION)) {
            context.addGetAttributeFunctionCalls(nodeTemplateName, functionCalls.get(ToscaConstants.GET_ATTRIBUTE_FUNCTION));
        }

        if (functionCalls.containsKey(ToscaConstants.GET_PROPERTY_FUNCTION)) {
            context.addGetPropertyFunctionCalls(nodeTemplateName, functionCalls.get(ToscaConstants.GET_PROPERTY_FUNCTION));
        }

        return property;
    }

    private Pair<Map<String, Set<ToscaGetterFunctionCallContext>>, Boolean> getAllToscaFunctionCalls(ToscaProperty property, Object propertyBody, ToscaTypeDefinition type, ToscaServiceTemplateParsingContext context) {
        Map<String, Set<ToscaGetterFunctionCallContext>> functionCalls = new HashMap<>();
        Boolean success = true;
        Pair<Map<String, Set<ToscaGetterFunctionCallContext>>, Boolean> functionCallsSuccess = new Pair<>(functionCalls, success);
        getAllToscaFunctionCallsRecursive(property, propertyBody, type, functionCallsSuccess, context);
        return functionCallsSuccess;
    }

    private void getAllToscaFunctionCallsRecursive(ToscaProperty property, Object propertyBody, ToscaTypeDefinition type, Pair<Map<String, Set<ToscaGetterFunctionCallContext>>, Boolean> functionCalls, ToscaServiceTemplateParsingContext context) {
        if (!functionCalls.second()) {
            return;
        }

        if (propertyBody instanceof List) {
            ToscaTypeDefinition entrySchema = ObjectUtils.defaultIfNull(type.getEntrySchema(), type);
            ToscaYamlHelper.asList(propertyBody).forEach(value -> getAllToscaFunctionCallsRecursive(property, value, entrySchema, functionCalls, context));
            return;
        }

        Map<String, Object> valueAsMap = ToscaYamlHelper.asMap(propertyBody);
        if (MapUtils.isEmpty(valueAsMap)) {
            return;
        }

        if (valueAsMap.size() == 1) {
            String function = valueAsMap.keySet().iterator().next();
            if (function.startsWith(ToscaConstants.FUNCTION_PREFIX) && !ToscaConstants.GETTER_FUNCTION_KEYS.contains(function)) {
                context.addError(String.format("Function call [%s] is not supported in property definitions.", function), function);
                return;
            } else if (ToscaConstants.GETTER_FUNCTION_KEYS.contains(function)) {
                if (validateToscaGetterFunctionCall(valueAsMap, type, context)) {
                    functionCalls.first().computeIfAbsent(function, k -> new HashSet<>()).add(new ToscaGetterFunctionCallContext(property, valueAsMap, type));
                } else {
                    functionCalls.second(false);
                }
                return;
            }
        }

        if (type.getKind() != ToscaTypeDefinition.Kind.DATA_TYPE) {
            ToscaTypeDefinition entrySchema = ObjectUtils.defaultIfNull(type.getEntrySchema(), type);
            valueAsMap.values().forEach(value -> getAllToscaFunctionCallsRecursive(property, value, entrySchema, functionCalls, context));
            return;
        }

        valueAsMap.forEach((fieldName, fieldValue) -> {
            ToscaPropertyDefinition fieldDefinition = type.getDataType().getProperties().get(fieldName);
            if (fieldDefinition != null) {
                getAllToscaFunctionCallsRecursive(property, fieldValue, fieldDefinition.getType(), functionCalls, context);
            }
        });
    }

    private boolean validateToscaGetterFunctionCall(Map<String, Object> functionCall, ToscaTypeDefinition expectedType, ToscaServiceTemplateParsingContext context) {
        String function = functionCall.keySet().iterator().next();
        if (function.equals(ToscaConstants.GET_ATTRIBUTE_FUNCTION) || function.equals(ToscaConstants.GET_PROPERTY_FUNCTION)) {
            List<?> args = function.equals(ToscaConstants.GET_ATTRIBUTE_FUNCTION) ? (List<?>) functionCall.get(ToscaConstants.GET_ATTRIBUTE_FUNCTION)
                    : (List<?>) functionCall.get(ToscaConstants.GET_PROPERTY_FUNCTION);
            if (args == null || args.size() != 2) {
                context.addError(String.format("The [%s] function call must have exactly two arguments.", function), function);
                return false;
            }
            return true;
        }

        String targetInputName = ToscaYamlHelper.asString(functionCall.get(ToscaConstants.GET_INPUT_FUNCTION));
        if (!context.getInputs().containsKey(targetInputName)) {
            context.addError(String.format("Function call [%s] is referencing a non-existent input [%s].", function, targetInputName), function);
            return false;
        }

        ToscaInputDefinition inputDefinition = context.getInputs().get(targetInputName);
        if (!expectedType.isAssignableFrom(inputDefinition.getType())) {
            context.addError(String.format("Function call [%s] is referencing an input [%s] that is not compatible with the associated property type [%s].", ToscaConstants.GET_INPUT_FUNCTION, targetInputName, expectedType), ToscaConstants.GET_INPUT_FUNCTION);
            return false;
        }

        return true;
    }

    private void parseNodeTemplateRequirements(String nodeTemplateName, List<?> requirements, ToscaServiceTemplateParsingContext context) {
        if (CollectionUtils.isEmpty(requirements)) {
            logger.debug("The node template [{}] does not have any requirement.", nodeTemplateName);
            return;
        }

        requirements.forEach(requirement -> {
            Map<String, Object> requirementMap = ToscaYamlHelper.asMap(requirement);
            if (MapUtils.isEmpty(requirementMap) || requirementMap.size() > 1 || !requirementMap.containsKey(ToscaConstants.DEPENDENCY)) {
                logger.debug("The node template [{}] has a requirement with invalid type. Skipping it.", nodeTemplateName);
                context.addError(String.format("The requirement of the node template [%s] is not valid.", nodeTemplateName), ToscaConstants.NODE_TEMPLATES);
                return;
            }

            String dependency = ToscaYamlHelper.asString(requirementMap.get(ToscaConstants.DEPENDENCY));
            if (dependency == null) {
                logger.debug("The node template [{}] has a requirement which has not specified a [dependency] key or its corresponding value. Skipping it.", nodeTemplateName);
                context.addError(String.format("Node template [%s] has a dependency with invalid name.", nodeTemplateName), ToscaConstants.NODE_TEMPLATES);
                return;
            }

            if (nodeTemplateName.equals(dependency)) {
                logger.debug("The node template [{}] is trying to establish a dependency with itself. Skipping it.", nodeTemplateName);
                context.addError(String.format("The node template [%s] cannot depend on itself.", nodeTemplateName), ToscaConstants.NODE_TEMPLATES);
            } else if (context.checkExistingDependency(nodeTemplateName, dependency)) {
                logger.debug("The node template [{}] is trying to establish a duplicate dependency with [{}]. Skipping it.", nodeTemplateName, dependency);
                context.addError(String.format("The dependency [%s] of the node template [%s] is duplicate.", dependency, nodeTemplateName), ToscaConstants.NODE_TEMPLATES);
            } else {
                logger.debug("Adding a dependency of the node template [{}] with [{}] to the parsing context.", nodeTemplateName, dependency);
                context.addNodeDependency(nodeTemplateName, ToscaYamlHelper.asString(dependency));
            }
        });
    }

    private void verifyToscaGetterFunctionCalls(Map<String, ToscaNodeTemplate> nodeTemplates, String functionName, ToscaServiceTemplateParsingContext context) {
        boolean isGetPropertyFunctionCall = ToscaConstants.GET_PROPERTY_FUNCTION.equals(functionName);
        Map<String, Set<ToscaGetterFunctionCallContext>> functionCalls = isGetPropertyFunctionCall ?
                context.getGetPropertyFunctionCalls() : context.getGetAttributeFunctionCalls();

        for (Map.Entry<String, Set<ToscaGetterFunctionCallContext>> entry : functionCalls.entrySet()) {
            ToscaNodeTemplate nodeTemplate = nodeTemplates.get(entry.getKey());
            Set<ToscaGetterFunctionCallContext> functionCallContexts = entry.getValue();

            if (!CollectionUtils.isEmpty(functionCallContexts)) {
                logger.debug("Checking [{}] function calls of the type [{}] contained in the node [{}].", functionCallContexts.size(), functionName, nodeTemplate.getName());
                functionCallContexts.forEach((functionCall) -> {
                    ToscaProperty unresolvedProperty = functionCall.getProperty();
                    verifyToscaGetterFunctionCall(nodeTemplate.getName(), functionCall, nodeTemplates, functionName, context);
                    if (isGetPropertyFunctionCall) {
                        nodeTemplate.addUnresolvedPropertyByGetProperty(unresolvedProperty);
                    } else {
                        nodeTemplate.addUnresolvedPropertyByGetAttribute(unresolvedProperty);
                    }
                });
            }
        }
    }

    private void verifyToscaGetterFunctionCall(String node, ToscaGetterFunctionCallContext functionCallContext, Map<String, ToscaNodeTemplate> nodeTemplates, String functionName, ToscaServiceTemplateParsingContext context) {
        Map<String, Object> functionCall = functionCallContext.getFunctionCall();
        List<?> arguments = (List<?>) functionCall.get(functionName);
        String targetNode = ToscaYamlHelper.asString(arguments.get(0));
        String targetField = ToscaYamlHelper.asString(arguments.get(1));

        if (node.equals(targetNode)) {
            context.addError(String.format("The node [%s] is trying to reference itself in the [%s] function call.", node, functionName), ToscaConstants.NODE_TEMPLATES);
            return;
        }

        if (!nodeTemplates.containsKey(targetNode)) {
            context.addError(String.format("The node [%s] has a dependency to a non-existent node [%s] in the [%s] function call.", node, targetNode, functionName), ToscaConstants.NODE_TEMPLATES);
            return;
        }

        ToscaNodeTemplate targetNodeTemplate = nodeTemplates.get(targetNode);
        if (ToscaConstants.GET_PROPERTY_FUNCTION.equals(functionName) && !targetNodeTemplate.hasProperty(targetField)) {
            context.addError(String.format("The node [%s] references an invalid property of the node [%s] in the [%s] function call.", node, targetNode, functionName), ToscaConstants.NODE_TEMPLATES);
            return;
        }

        if (ToscaConstants.GET_ATTRIBUTE_FUNCTION.equals(functionName) && !targetNodeTemplate.getType().hasAttribute(targetField)) {
            context.addError(String.format("The node [%s] references an invalid attribute of the node [%s] in the [%s] function call.", node, targetNode, functionName), ToscaConstants.NODE_TEMPLATES);
            return;
        }

        ToscaFieldDefinition targetFieldDefinition = ToscaConstants.GET_PROPERTY_FUNCTION.equals(functionName) ?
                targetNodeTemplate.getProperty(targetField).getDefinition() : targetNodeTemplate.getType().getAttributeDefinition(targetField);
        if (!functionCallContext.getExpectedType().isAssignableFrom(targetFieldDefinition.getType())) {
            context.addError(String.format("Unmatching types between [node: %s, field: %s] and [node: %s, field: %s] in the [%s] function call.", node, functionCallContext.getProperty().getDefinition().getName(), targetNode, targetField, functionName), ToscaConstants.NODE_TEMPLATES);
            return;
        }

        if (context.checkExistingDependency(node, targetNode)) {
            logger.debug("The node [{}] already has a dependency with [{}]. Skipping it.", node, targetNode);
        } else {
            logger.debug("Successfully parsed the function call [{}] of the node [{}]. Adding the dependency with [{}] to the parsing context.", functionCall, node, targetNode);
            context.addNodeDependency(node, targetNode);
        }
    }

    private Map<String, Set<ToscaNodeTemplate>> buildDependencyGraph(Map<String, ToscaNodeTemplate> nodeTemplates, ToscaServiceTemplateParsingContext context) {
        logger.debug("Building the dependency graph of the TOSCA service template.");
        Map<String, Set<ToscaNodeTemplate>> graph = new HashMap<>();
        for (Map.Entry<String, Set<String>> nodeDependencies : context.getNodeDependencies().entrySet()) {
            String node = nodeDependencies.getKey();
            Set<String> dependencies = nodeDependencies.getValue();
            logger.debug("The node [{}] has the following dependencies: {}", node, dependencies);

            dependencies.forEach(dependency -> {
                if (!nodeTemplates.containsKey(dependency)) {
                    logger.debug("The node [{}] is trying to establish a dependency with a non-existent node [{}]. Skipping it.", node, dependency);
                    context.addError(String.format("The node [%s] has a dependency to a non-existent node [%s].", node, dependency), ToscaConstants.NODE_TEMPLATES);
                    return;
                }

                if (context.getNodeDependencies().getOrDefault(dependency, new HashSet<>()).contains(node)) {
                    logger.debug("The target node [{}] already has a dependency with the source node [{}]. Thus, unable to establish relationship, because it would create a cycle in the graph. Skipping it.", dependency, node);
                    context.addError(String.format("The nodes [%s] and [%s] have a circular dependency with each other.", node, dependency), ToscaConstants.NODE_TEMPLATES);
                    return;
                }

                logger.debug("Adding the relationship between the nodes [{}] and [{}] to the dependency graph.", node, dependency);
                graph.computeIfAbsent(node, (n) -> new HashSet<>()).add(nodeTemplates.get(dependency));
            });
        }

        logger.info("Dependency graph of the TOSCA service template has been successfully built with [{}] arcs/dependencies.", graph.values().stream().mapToLong(Set::size).sum());
        return graph;
    }

    /**
     * Checks whether all the required TOSCA keys are present in {@param content}.
     * @param content The YAML content to be validated.
     * @param requiredKeys The TOSCA keys that must be present in {@param content}.
     * @param templateSection The section of the service template being validated. Only used for building the error messages.
     * @param context The service template parsing context ({@link ToscaServiceTemplateParsingContext}) to which error messages might be added.
     * @return {@code true} if there are missing required TOSCA keys in {@param content}, {@code false} otherwise.
     */
    protected boolean checkMissingRequiredToscaKeys(Map<String, Object> content, Set<String> requiredKeys, String templateSection, ToscaServiceTemplateParsingContext context) {
        List<String> errorMessages = new ArrayList<>();
        requiredKeys.stream()
                .filter(key -> !content.containsKey(key))
                .forEach(key -> errorMessages.add(String.format("The key [%s] is required.", key)));

        if (errorMessages.isEmpty()) {
            return false;
        }

        context.addErrors(errorMessages, templateSection);
        return true;
    }

    /**
     * Validate whether all the YAML keys present in {@param content} are known TOSCA keys.
     * @param content The YAML content to be validated.
     * @param knownToscaKeys The known TOSCA keys that can be present in{@param content} .
     * @param templateSection The section of the service template being validated. Only used for building the error message.
     * @param context The service template parsing context ({@link ToscaServiceTemplateParsingContext}) to which error messages might be added.
     */
    protected void validateKnownToscaKeys(Map<String, Object> content, Set<String> knownToscaKeys, String templateSection, ToscaServiceTemplateParsingContext context) {
        logger.debug("Validating whether the provided YAML content {} only has the following known TOSCA keys {}", content.keySet(), knownToscaKeys);

        content.keySet().stream()
                .filter(key -> !knownToscaKeys.contains(key))
                .forEach(key -> context.addError(String.format("Unknown key [%s].", key), templateSection));
    }
}

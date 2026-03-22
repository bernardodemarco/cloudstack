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
        ToscaServiceTemplateParsingContext context = new ToscaServiceTemplateParsingContext(toscaProfile);

        checkRootServiceTemplateYamlKeys(rawToscaTemplate, context);
        Map<String, Object> rawServiceTemplate = ToscaYamlHelper.asMap(rawToscaTemplate.get(ToscaConstants.SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY));
        Map<String, ToscaInputDefinition> inputs = parseInputs(ToscaYamlHelper.asMap(rawServiceTemplate.get(ToscaConstants.SERVICE_TEMPLATE_INPUTS_KEY)), context);
        Map<String, ToscaNodeTemplate> nodeTemplates = parseNodeTemplates(ToscaYamlHelper.asMap(rawServiceTemplate.get(ToscaConstants.SERVICE_TEMPLATE_NODE_TEMPLATES_KEY)), context);

        verifyUnresolvedProperties(nodeTemplates, "$get_property", context);
        verifyUnresolvedProperties(nodeTemplates, "$get_attribute", context);
        Map<String, Set<ToscaNodeTemplate>> dependencyGraph = buildDependencyGraph(nodeTemplates, context);

        if (context.hasErrors()) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }

        return new ToscaServiceTemplate(nodeTemplates, dependencyGraph, inputs, context.getUnresolvedByGetInput());
    }

    /**
     * Checks whether all the YAML keys present in the root and service_template levels of
     * TOSCA service templates are known and whether all the required ones were provided.
     * @param template The TOSCA service template to be validated.
     * @param context The service template parsing context ({@link ToscaServiceTemplateParsingContext}) for error handling purposes.
     * @throws InvalidParameterValueException When unknown keys are present or required keys are missing.
     */
    private void checkRootServiceTemplateYamlKeys(Map<String, Object> template, ToscaServiceTemplateParsingContext context) {
        validateKnownToscaKeys(template, Set.of(ToscaConstants.SERVICE_TEMPLATE_TOSCA_VERSION_KEY, ToscaConstants.SERVICE_TEMPLATE_DESCRIPTION_KEY, ToscaConstants.SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY), "Root level", context);
        boolean missingRootRequiredKeys = checkMissingRequiredToscaKeys(template, Set.of(ToscaConstants.SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY), "Root level", context);
        if (missingRootRequiredKeys) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }

        Map<String, Object> serviceTemplateSection = ToscaYamlHelper.asMap(template.get(ToscaConstants.SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY));
        validateKnownToscaKeys(serviceTemplateSection, Set.of(ToscaConstants.SERVICE_TEMPLATE_NODE_TEMPLATES_KEY, ToscaConstants.SERVICE_TEMPLATE_INPUTS_KEY, ToscaConstants.SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY), "Service template level", context);
        boolean missingServiceTemplateRequiredKeys = checkMissingRequiredToscaKeys(serviceTemplateSection, Set.of(ToscaConstants.SERVICE_TEMPLATE_NODE_TEMPLATES_KEY), "Service template level", context);
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
        Map<String, ToscaInputDefinition> inputDefinitions = new HashMap<>();
        for (Map.Entry<String, Object> input : inputs.entrySet()) {
            ToscaInputDefinition inputDefinition = parseInput(input.getKey(), ToscaYamlHelper.asMap(input.getValue()), context);
            if (inputDefinition != null) {
                inputDefinitions.put(inputDefinition.getName(), inputDefinition);
            }
        }

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
                Set.of(ToscaConstants.FIELDS_DESCRIPTION_KEY, ToscaConstants.FIELDS_TYPE_KEY, ToscaConstants.FIELDS_VALIDATION_KEY, ToscaConstants.FIELDS_ENTRY_DEFAULT_VALUE_KEY),
                "inputs section", context);

        ToscaTypeDefinition type = toscaFieldParser.parseType(body, null);
        if (type == null) {
            context.addError(String.format("The type of the input [%s] was not specified or it is not supported.", name), "inputs section");
            return null;
        }

        Object defaultValue = body.get(ToscaConstants.FIELDS_ENTRY_DEFAULT_VALUE_KEY);
        if (defaultValue != null && !type.isCompatibleWith(defaultValue)) {
            context.addError(String.format("The provided default value [%s] for the input [%s] is not compatible with the input type [%s].", defaultValue, name, type), "inputs section");
            return null;
        }

        Map<String, Object> rawValidation = ToscaYamlHelper.asMap(body.get(ToscaConstants.FIELDS_VALIDATION_KEY));
        ToscaFunction.ToscaBooleanFunction validation = toscaFieldParser.parseToscaBooleanFunction(rawValidation);
        if (MapUtils.isNotEmpty(rawValidation) && validation == null) {
            context.addError(String.format("The validation function of the input [%s] is not valid.", name), "inputs section");
            return null;
        }

        String description = ToscaYamlHelper.asString(body.get(ToscaConstants.FIELDS_DESCRIPTION_KEY));
        return new ToscaInputDefinition(name, description, type, defaultValue, validation);
    }

    protected Map<String, ToscaNodeTemplate> parseNodeTemplates(Map<String, Object> nodeTemplates, ToscaServiceTemplateParsingContext context) {
        Map<String, ToscaNodeTemplate> nodeTemplateDefinitions = new HashMap<>();
        for (Map.Entry<String, Object> nodeTemplate : nodeTemplates.entrySet()) {
            ToscaNodeTemplate toscaNodeTemplate = parseNodeTemplate(nodeTemplate.getKey(), ToscaYamlHelper.asMap(nodeTemplate.getValue()), context);
            if (toscaNodeTemplate != null) {
                nodeTemplateDefinitions.put(toscaNodeTemplate.getName(), toscaNodeTemplate);
            }
        }
        return nodeTemplateDefinitions;
    }

    private ToscaNodeTemplate parseNodeTemplate(String nodeTemplateName, Map<String, Object> body, ToscaServiceTemplateParsingContext context) {
        validateKnownToscaKeys(body, Set.of(ToscaConstants.PROPERTIES_KEY, ToscaConstants.FIELDS_TYPE_KEY, ToscaConstants.NODE_TEMPLATES_REQUIREMENTS_KEY), "node templates section", context);
        boolean missingRequiredNodeTemplateKeys = checkMissingRequiredToscaKeys(body, Set.of(ToscaConstants.PROPERTIES_KEY, ToscaConstants.FIELDS_TYPE_KEY), "node templates section", context);
        if (missingRequiredNodeTemplateKeys) {
            return null;
        }

        String nodeTypeName = ToscaYamlHelper.asString(body.get(ToscaConstants.FIELDS_TYPE_KEY));
        if (nodeTypeName == null || !context.getProfile().containsKey(nodeTypeName)) {
            context.addError(String.format("The node type [%s] is not valid.", nodeTypeName), "node templates section");
            return null;
        }
        ToscaNodeType nodeType = context.getProfile().get(nodeTypeName);

        parseNodeTemplateRequirements(nodeTemplateName, ToscaYamlHelper.asList(body.get(ToscaConstants.NODE_TEMPLATES_REQUIREMENTS_KEY)), context);
        Map<String, ToscaProperty> properties = parseNodeTemplateProperties(nodeTemplateName, ToscaYamlHelper.asMap(body.get(ToscaConstants.PROPERTIES_KEY)), nodeType, context);
        if (properties == null) {
            return null;
        }

        return new ToscaNodeTemplate(nodeTemplateName, nodeType, properties);
    }

    private Map<String, ToscaProperty> parseNodeTemplateProperties(String nodeTemplateName, Map<String, Object> properties, ToscaNodeType nodeType, ToscaServiceTemplateParsingContext context) {
        validateKnownToscaKeys(properties, nodeType.getProperties().keySet(), nodeTemplateName + " declaration", context);
        Set<String> nodeTypeRequiredProperties = nodeType.getRequiredPropertyNames();
        boolean missingRequiredProperties = checkMissingRequiredToscaKeys(properties, nodeTypeRequiredProperties, nodeTemplateName + " declaration", context);
        if (missingRequiredProperties) {
            return null;
        }

        Map<String, ToscaProperty> propertyDefinitions = new HashMap<>();
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            String propertyName = property.getKey();
            if (!nodeType.getProperties().containsKey(propertyName)) {
                continue;
            }
            ToscaProperty toscaProperty = parseProperty(propertyName, property.getValue(), nodeType.getProperties().get(propertyName), nodeTemplateName, context);
            if (toscaProperty != null) {
                propertyDefinitions.put(propertyName, toscaProperty);
            }
        }

        return propertyDefinitions;
    }

    private ToscaProperty parseProperty(String propertyName, Object propertyBody, ToscaPropertyDefinition propertyDefinition, String nodeTemplateName, ToscaServiceTemplateParsingContext context) {
        Map<String, Object> body = ToscaYamlHelper.asMap(propertyBody);
        boolean isFunctionCall = body.size() == 1 && body.keySet().iterator().next().startsWith("$");
        if (!isFunctionCall) {
            return parsePropertyValue(propertyName, propertyBody, propertyDefinition, nodeTemplateName, context);
        }

        if (body.containsKey("$get_input")) {
            return parseGetInputPropertyValue(propertyName, body, propertyDefinition, nodeTemplateName, context);
        }

        return parseGetPropertyAndGetAttributePropertyValue(propertyName, body, propertyDefinition, nodeTemplateName, context);
    }

    private ToscaProperty parsePropertyValue(String propertyName, Object propertyBody, ToscaPropertyDefinition propertyDefinition, String nodeTemplateName, ToscaServiceTemplateParsingContext context) {
        ToscaTypeDefinition type = propertyDefinition.getType();
        if (!type.isCompatibleWith(propertyBody)) {
            context.addError(String.format("The provided value [%s] for the property [%s] of the [%s] node template is not compatible with the property type [%s].", propertyBody, propertyName, nodeTemplateName, propertyDefinition.getType()), nodeTemplateName + " declaration");
            return null;
        }

        ToscaFunction.ToscaBooleanFunction validationFunction = propertyDefinition.getValidation();
        if (validationFunction != null && !validationFunction.evaluate(propertyBody)) {
            context.addError(String.format("The provided value [%s] for the property [%s] of the [%s] node template is not valid according to the property validation function [%s].", propertyBody, propertyName, nodeTemplateName, validationFunction), nodeTemplateName + " declaration");
            return null;
        }

        return new ToscaProperty(propertyDefinition, propertyBody, propertyBody);
    }

    private ToscaProperty parseGetInputPropertyValue(String propertyName, Map<String, Object> propertyBody, ToscaPropertyDefinition propertyDefinition, String nodeTemplateName, ToscaServiceTemplateParsingContext context) {
        String targetInputName = ToscaYamlHelper.asString(propertyBody.get("$get_input"));
        if (!context.getInputs().containsKey(targetInputName)) {
            context.addError(String.format("The input [%s] referenced in the property [%s] of the [%s] node template is not valid.", targetInputName, propertyName, nodeTemplateName), nodeTemplateName + " declaration");
            return null;
        }

        ToscaInputDefinition inputDefinition = context.getInputs().get(targetInputName);
        if (!propertyDefinition.getType().isAssignableFrom(inputDefinition.getType())) {
            context.addError(String.format("The input [%s] referenced in the property [%s] of the [%s] node template is not compatible with the property type [%s].", targetInputName, propertyName, nodeTemplateName, propertyDefinition.getType()), nodeTemplateName + " declaration");
            return null;
        }

        ToscaProperty property = new ToscaProperty(propertyDefinition, propertyBody, null);
        context.addUnresolvedByGetInput(nodeTemplateName, property);
        return property;
    }

    private ToscaProperty parseGetPropertyAndGetAttributePropertyValue(String propertyName, Map<String, Object> body, ToscaPropertyDefinition propertyDefinition, String nodeTemplateName, ToscaServiceTemplateParsingContext context) {
        boolean getPropertyFunctionCall = body.containsKey("$get_property");
        if (!getPropertyFunctionCall && !body.containsKey("$get_attribute")) {
            context.addError(String.format("The property [%s] of the [%s] node template is not valid.", propertyName, nodeTemplateName), nodeTemplateName + " declaration");
            return null;
        }

        List<?> args = getPropertyFunctionCall ? (List<?>) body.get("$get_property") : (List<?>) body.get("$get_attribute");
        if (args == null || args.size() != 2) {
            context.addError(String.format("The [%s] function must have exactly 2 arguments.", getPropertyFunctionCall ? "$get_property" : "$get_attribute"), nodeTemplateName + " declaration");
            return null;
        }

        ToscaProperty property = new ToscaProperty(propertyDefinition, body, null);
        if (getPropertyFunctionCall) {
            context.addUnresolvedByGetProperty(nodeTemplateName, property);
        } else {
            context.addUnresolvedByGetAttribute(nodeTemplateName, property);
        }

        return property;
    }

    private void parseNodeTemplateRequirements(String nodeTemplateName, List<?> requirements, ToscaServiceTemplateParsingContext context) {
        if (CollectionUtils.isEmpty(requirements)) {
            return;
        }

        requirements.forEach(requirement -> {
            Map<String, Object> requirementMap = ToscaYamlHelper.asMap(requirement);
            if (MapUtils.isEmpty(requirementMap) || requirementMap.size() > 1 || !requirementMap.containsKey(ToscaConstants.DEPENDENCY_KEY)) {
                context.addError(String.format("The requirement of the node template [%s] is not valid.", nodeTemplateName), "node templates section");
                return;
            }

            String dependency = ToscaYamlHelper.asString(requirementMap.get(ToscaConstants.DEPENDENCY_KEY));
            if (dependency == null) {
                context.addError(String.format("Node template [%s] has a dependency with invalid name.", nodeTemplateName), "node templates section");
                return;
            }

            if (nodeTemplateName.equals(dependency)) {
                context.addError(String.format("The node template [%s] cannot depend on itself.", nodeTemplateName), "node templates section");
            } else if (context.checkExistingDependency(nodeTemplateName, dependency)) {
                context.addError(String.format("The dependency [%s] of the node template [%s] is duplicate.", dependency, nodeTemplateName), "node templates section");
            } else {
                context.addNodeDependency(nodeTemplateName, ToscaYamlHelper.asString(dependency));
            }
        });
    }

    private void verifyUnresolvedProperties(Map<String, ToscaNodeTemplate> nodeTemplates, String functionName, ToscaServiceTemplateParsingContext context) {
        boolean isGetPropertyFunctionCall = "$get_property".equals(functionName);
        Map<String, Set<ToscaProperty>> unresolvedProperties = isGetPropertyFunctionCall ? context.getUnresolvedByGetProperty() : context.getUnresolvedByGetAttribute();
        for (Map.Entry<String, Set<ToscaProperty>> entry : unresolvedProperties.entrySet()) {
            ToscaNodeTemplate nodeTemplate = nodeTemplates.get(entry.getKey());
            Set<ToscaProperty> properties = entry.getValue();
            if (!CollectionUtils.isEmpty(properties)) {
                checkUnresolvedPropertiesOfANode(nodeTemplate.getName(), properties, nodeTemplates, functionName, context);
                if (isGetPropertyFunctionCall) {
                    nodeTemplate.setUnresolvedPropertiesByGetProperty(properties);
                } else {
                    nodeTemplate.setUnresolvedPropertiesByGetAttribute(properties);
                }
            }
        }
    }

    private void checkUnresolvedPropertiesOfANode(String node, Set<ToscaProperty> unresolvedProperties, Map<String, ToscaNodeTemplate> nodeTemplates, String functionName, ToscaServiceTemplateParsingContext context) {
        unresolvedProperties.forEach(property -> {
            Map<String, Object> functionCall = ToscaYamlHelper.asMap(property.getRawValue());
            List<?> arguments = (List<?>) functionCall.get(functionName);
            String targetNode = ToscaYamlHelper.asString(arguments.get(0));
            String targetField = ToscaYamlHelper.asString(arguments.get(1));

            if (node.equals(targetNode)) {
                context.addError(String.format("The node [%s] cannot reference itself.", node), "node templates section");
                return;
            }

            if (!nodeTemplates.containsKey(targetNode)) {
                context.addError(String.format("The node [%s] has a dependency to a non-existent node [%s].", node, targetNode), "node templates section");
                return;
            }

            ToscaNodeTemplate targetNodeTemplate = nodeTemplates.get(targetNode);
            if ("$get_property".equals(functionName) && !targetNodeTemplate.hasProperty(targetField)) {
                context.addError(String.format("The node [%s] references an invalid property of the node [%s].", node, targetNode), "node templates section");
                return;
            }

            if ("$get_attribute".equals(functionName) && !targetNodeTemplate.getType().hasAttribute(targetField)) {
                context.addError(String.format("The node [%s] references an invalid attribute of the node [%s].", node, targetNode), "node templates section");
                return;
            }

            ToscaFieldDefinition targetFieldDefinition = "$get_property".equals(functionName) ?
                    targetNodeTemplate.getProperty(targetField).getDefinition() : targetNodeTemplate.getType().getAttributeDefinition(targetField);
            if (!property.getDefinition().getType().isAssignableFrom(targetFieldDefinition.getType())) {
                context.addError(String.format("Unmatching types between [node: %s, field: %s] and [node: %s, field: %s].", node, property.getDefinition().getName(), targetNode, targetField), "node templates section");
                return;
            }

            if (!context.checkExistingDependency(node, targetNode)) {
                context.addNodeDependency(node, targetNode);
            }
        });
    }

    private Map<String, Set<ToscaNodeTemplate>> buildDependencyGraph(Map<String, ToscaNodeTemplate> nodeTemplates, ToscaServiceTemplateParsingContext context) {
        Map<String, Set<ToscaNodeTemplate>> graph = new HashMap<>();
        for (Map.Entry<String, Set<String>> nodeDependencies : context.getNodeDependencies().entrySet()) {
            String node = nodeDependencies.getKey();
            Set<String> dependencies = nodeDependencies.getValue();

            dependencies.forEach(dependency -> {
                if (!nodeTemplates.containsKey(dependency)) {
                    context.addError(String.format("The node [%s] has a dependency to a non-existent node [%s].", node, dependency), "node templates section");
                    return;
                }

                if (context.getNodeDependencies().getOrDefault(dependency, new HashSet<>()).contains(node)) {
                    context.addError(String.format("The nodes [%s] and [%s] have a circular dependency with each other.", node, dependency), "node templates section");
                    return;
                }

                graph.computeIfAbsent(node, (n) -> new HashSet<>()).add(nodeTemplates.get(dependency));
            });
        }
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
        content.keySet().stream()
                .filter(key -> !knownToscaKeys.contains(key))
                .forEach(key -> context.addError(String.format("Unknown key [%s].", key), templateSection));
    }
}

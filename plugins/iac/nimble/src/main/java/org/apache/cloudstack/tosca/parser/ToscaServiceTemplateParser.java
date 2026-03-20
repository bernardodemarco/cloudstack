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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        Map<String, ToscaNodeTemplate> nodeTemplates = parseNodeTemplates(ToscaYamlHelper.asMap(rawServiceTemplate.get(ToscaConstants.SERVICE_TEMPLATE_INPUTS_KEY)), context);

        checkUnresolvedProperties(nodeTemplates, "$get_property", context);
        checkUnresolvedProperties(nodeTemplates, "$get_attribute", context);
        Map<String, Set<ToscaNodeTemplate>> serviceTemplateDependencies = buildGraphDependencies(nodeTemplates, context);

        if (context.hasErrors()) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }

        return new ToscaServiceTemplate();
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
        validateRequiredToscaKeys(template, Set.of(ToscaConstants.SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY), "Root level", context);
        if (context.hasErrors()) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }

        Map<String, Object> serviceTemplateSection = ToscaYamlHelper.asMap(template.get(ToscaConstants.SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY));
        validateKnownToscaKeys(serviceTemplateSection, Set.of(ToscaConstants.SERVICE_TEMPLATE_NODE_TEMPLATES_KEY, ToscaConstants.SERVICE_TEMPLATE_INPUTS_KEY, ToscaConstants.SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY), "Service template level", context);
        validateRequiredToscaKeys(serviceTemplateSection, Set.of(ToscaConstants.SERVICE_TEMPLATE_NODE_TEMPLATES_KEY), "Service template level", context);
        if (context.hasErrors()) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }
    }

    private void checkUnresolvedProperties(Map<String, ToscaNodeTemplate> nodeTemplates, String function, ToscaServiceTemplateParsingContext context) {
        Map<String, Set<ToscaProperty>> unresolvedProperties = "$get_property".equals(function) ? context.getUnresolvedByGetProperty() : context.getUnresolvedByGetAttribute();
        for (Map.Entry<String, Set<ToscaProperty>> entry : unresolvedProperties.entrySet()) {
            Set<ToscaProperty> properties = entry.getValue();
            if (!CollectionUtils.isEmpty(properties)) {
                checkUnresolvedPropertiesOfANode(entry.getKey(), properties, nodeTemplates, function, context);
            }
        }
    }

    private void checkUnresolvedPropertiesOfANode(String node, Set<ToscaProperty> unresolvedProperties, Map<String, ToscaNodeTemplate> nodeTemplates, String function, ToscaServiceTemplateParsingContext context) {
        unresolvedProperties.forEach(property -> {
            Map<String, Object> functionCall = ToscaYamlHelper.asMap(property.getRawValue());
            List<?> arguments = (List<?>) functionCall.get(function);
            String targetNode = ToscaYamlHelper.asString(arguments.get(0));
            String targetField = ToscaYamlHelper.asString(arguments.get(1));

            if (!nodeTemplates.containsKey(targetNode)) {
                context.addError(String.format("The node [%s] has a dependency to a non-existent node [%s].", node, targetNode), "node templates section");
                return;
            }

            ToscaNodeTemplate targetNodeTemplate = nodeTemplates.get(targetNode);
            if ("$get_property".equals(function) && !targetNodeTemplate.hasProperty(targetField)) {
                context.addError(String.format("The node [%s] references an invalid property of the node [%s].", node, targetNode), "node templates section");
                return;
            }

            if ("$get_attribute".equals(function) && !targetNodeTemplate.getType().hasAttribute(targetField)) {
                context.addError(String.format("The node [%s] references an invalid attribute of the node [%s].", node, targetNode), "node templates section");
                return;
            }

            ToscaFieldDefinition targetFieldDefinition = "$get_property".equals(function) ?
                    targetNodeTemplate.getProperty(targetField).getDefinition() : targetNodeTemplate.getType().getAttributeDefinition(targetField);
            if (!property.getDefinition().getType().isAssignableFrom(targetFieldDefinition.getType())) {
                context.addError(String.format("Unmatching types between [node: %s, field: %s] and [node: %s, field: %s].", node, property.getDefinition().getName(), targetNode, targetField), "node templates section");
                return;
            }

            context.addNodeDependency(node, targetNode);
        });
    }

    private Map<String, Set<ToscaNodeTemplate>> buildGraphDependencies(Map<String, ToscaNodeTemplate> nodeTemplates, ToscaServiceTemplateParsingContext context) {
        Map<String, Set<ToscaNodeTemplate>> serviceTemplateDependencies = new HashMap<>();
        for (Map.Entry<String, Set<String>> nodeDependencies : context.getNodeDependencies().entrySet()) {
            String node = nodeDependencies.getKey();
            Set<String> dependencies = nodeDependencies.getValue();

            dependencies.forEach(dependency -> {
                if (!nodeTemplates.containsKey(dependency)) {// and what about self referencing?
                    context.addError(String.format("The node [%s] has a dependency to a non-existent node [%s].", node, dependency), "node templates section");
                    return;
                }

                if (context.getNodeDependencies().getOrDefault(dependency, new HashSet<>()).contains(node)) {
                    context.addError(String.format("The nodes [%s] and [%s] have a circular dependency with each other.", node, dependency), "node templates section");
                    return;
                }

                serviceTemplateDependencies.computeIfAbsent(node, (n) -> new HashSet<>()).add(nodeTemplates.get(dependency));
            });
        }
        return serviceTemplateDependencies;
    }

    private Map<String, ToscaNodeTemplate> parseNodeTemplates(Map<String, Object> nodeTemplates, ToscaServiceTemplateParsingContext context) {
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
        String nodeTypeName = ToscaYamlHelper.asString(body.get(ToscaConstants.FIELDS_TYPE_KEY));
        if (nodeTypeName == null || !context.getProfile().containsKey(nodeTypeName)) {
            context.addError(String.format("The node type [%s] is not valid.", nodeTypeName), "node templates section");
            return null;
        }

        ToscaNodeType nodeType = context.getProfile().get(nodeTypeName);
        validateKnownToscaKeys(body, Set.of(ToscaConstants.PROPERTIES_KEY, ToscaConstants.NODE_TEMPLATES_REQUIREMENTS_KEY), "node templates section", context);
        validateRequiredToscaKeys(body, Set.of(ToscaConstants.PROPERTIES_KEY), "node templates section", context);

        Map<String, ToscaProperty> properties = parseNodeTemplateProperties(nodeTemplateName, ToscaYamlHelper.asMap(body.get(ToscaConstants.PROPERTIES_KEY)), nodeType, context);
        parseNodeTemplateRequirements(nodeTemplateName, ToscaYamlHelper.asMap(body.get(ToscaConstants.NODE_TEMPLATES_REQUIREMENTS_KEY)), context);
        return new ToscaNodeTemplate(nodeTemplateName, nodeType, properties);
    }

    private void parseNodeTemplateRequirements(String nodeTemplateName, Map<String, Object> requirements, ToscaServiceTemplateParsingContext context) {
        if (MapUtils.isEmpty(requirements)) {
            return;
        }

        validateKnownToscaKeys(requirements, Set.of(ToscaConstants.DEPENDENCY_KEY), "node templates section", context);
        validateRequiredToscaKeys(requirements, Set.of(ToscaConstants.DEPENDENCY_KEY), "node templates section", context);
        List<?> dependencies = ToscaYamlHelper.asList(requirements.get(ToscaConstants.DEPENDENCY_KEY));
        if (dependencies == null) {
//            no error message will be returned here?
            return;
        }

        dependencies.forEach(dependency -> {
            context.addNodeDependency(nodeTemplateName, ToscaYamlHelper.asString(dependency));
        });
    }

    //    what about duplicate node templates?
    private Map<String, ToscaProperty> parseNodeTemplateProperties(String nodeTemplateName, Map<String, Object> properties, ToscaNodeType nodeType, ToscaServiceTemplateParsingContext context) {
        validateKnownToscaKeys(properties, nodeType.getProperties().keySet(), nodeTemplateName + " declaration", context);
        Set<String> nodeTypeRequiredProperties = nodeType.getProperties().values().stream()
                .filter(ToscaPropertyDefinition::isRequired).map(ToscaPropertyDefinition::getName).collect(Collectors.toSet());
        validateRequiredToscaKeys(properties, nodeTypeRequiredProperties, nodeTemplateName + " declaration", context);

        Map<String, ToscaProperty> propertyDefinitions = new HashMap<>();
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            String propertyName = property.getKey();
            ToscaProperty toscaProperty = parseProperty(propertyName, property.getValue(), nodeType.getProperties().get(propertyName), nodeTemplateName, context);
            if (toscaProperty != null) {
                propertyDefinitions.put(propertyName, toscaProperty);
            }
        }

        return propertyDefinitions;
    }

    private ToscaProperty parseProperty(String propertyName, Object propertyBody, ToscaPropertyDefinition propertyDefinition, String nodeTemplateName, ToscaServiceTemplateParsingContext context) {
        if (!(propertyBody instanceof Map)) {
            return parsePropertyValue(propertyName, propertyBody, propertyDefinition, nodeTemplateName, context);
        }

        Map<String, Object> body = ToscaYamlHelper.asMap(propertyBody);
        if (body.containsKey("$get_input")) {
            return parseGetInputPropertyValue(propertyName, body, propertyDefinition, nodeTemplateName, context);
        }

        return parseGetPropertyAndGetAttributePropertyValue(propertyName, body, propertyDefinition, nodeTemplateName, context);
    }

    private ToscaProperty parseGetPropertyAndGetAttributePropertyValue(String propertyName, Map<String, Object> body, ToscaPropertyDefinition propertyDefinition, String nodeTemplateName, ToscaServiceTemplateParsingContext context) {
        boolean getPropertyFunctionCall = body.containsKey("$get_property");
        if (getPropertyFunctionCall || body.containsKey("$get_attribute")) {
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

        context.addError(String.format("The property [%s] of the [%s] node template is not valid.", propertyName, nodeTemplateName), nodeTemplateName + " declaration");
        return null;
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

    private ToscaProperty parsePropertyValue(String propertyName, Object propertyBody, ToscaPropertyDefinition propertyDefinition, String nodeTemplateName, ToscaServiceTemplateParsingContext context) {
        if (!propertyDefinition.getType().isCompatibleWith(propertyBody)) {
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

    /**
     * Validate whether all the required TOSCA keys are present in {@param content}.
     * @param content The YAML content to be validated.
     * @param requiredKeys The TOSCA keys that must be present in {@param content}.
     * @param templateSection The section of the service template being validated. Only used for building the error message.
     * @param context The service template parsing context ({@link ToscaServiceTemplateParsingContext}) to which error messages might be added.
     */
    protected void validateRequiredToscaKeys(Map<String, Object> content, Set<String> requiredKeys, String templateSection, ToscaServiceTemplateParsingContext context) {
        requiredKeys.stream()
                .filter(key -> !content.containsKey(key))
                .forEach(key -> context.addError(String.format("The key [%s] is required.", key), templateSection));
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

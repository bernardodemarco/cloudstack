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
import org.apache.cloudstack.tosca.functions.ToscaBooleanFunctions;
import org.apache.cloudstack.tosca.functions.ToscaFunction;
import org.apache.cloudstack.tosca.model.ToscaAttributeDefinition;
import org.apache.cloudstack.tosca.model.ToscaCollectionType;
import org.apache.cloudstack.tosca.model.ToscaDataTypeDefinition;
import org.apache.cloudstack.tosca.model.ToscaFieldDefinition;
import org.apache.cloudstack.tosca.model.ToscaInputDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaProperty;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ToscaParser {
    private final Logger logger = LogManager.getLogger(ToscaParser.class);

    protected enum TypeOfToscaField {
        ATTRIBUTE, PROPERTY
    }

    private static final String DATA_TYPES_KEY = "data_types";
    private static final String NODE_TYPES_KEY = "node_types";

    private static final String NODE_TYPES_ATTRIBUTES_KEY = "attributes";
    private static final String PROPERTIES_KEY = "properties";
    private static final String NODE_TEMPLATES_REQUIREMENTS_KEY = "requirements";

    private static final String FIELDS_TYPE_KEY = "type";
    private static final String FIELDS_REQUIRED_KEY = "required";
    private static final String FIELDS_VALIDATION_KEY = "validation";
    private static final String FIELDS_DESCRIPTION_KEY = "description";
    private static final String FIELDS_ENTRY_SCHEMA_KEY = "entry_schema";
    private static final String FIELDS_ENTRY_DEFAULT_VALUE_KEY = "default_value";

    private static final String SERVICE_TEMPLATE_TOSCA_VERSION_KEY = "tosca_definitions_version";
    private static final String SERVICE_TEMPLATE_DESCRIPTION_KEY = "description";
    private static final String SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY = "service_template";
    private static final String SERVICE_TEMPLATE_INPUTS_KEY = "inputs";
    private static final String SERVICE_TEMPLATE_NODE_TEMPLATES_KEY = "node_templates";

    /**
     * Parses a node type definition file.
     * @param nodeTypeContent The YAML content of the node type definition file.
     * @return a {@link ToscaNodeType} representing the node type.
     */
    public ToscaNodeType parseNodeTypeDefinitionFile(String nodeTypeContent) {
        Object rawYaml = ToscaYamlHelper.loadYaml(nodeTypeContent);
        Map<String, Object> yamlRoot = ToscaYamlHelper.asMap(rawYaml);
        Map<String, ToscaDataTypeDefinition> dataTypes = parseDataTypes(yamlRoot);
        return parseNodeType(yamlRoot, dataTypes);
    }

    /**
     * Parses all data types defined in the TOSCA file.
     * @param yamlRoot The root of the YAML file.
     * @return a map of {@link ToscaDataTypeDefinition} representing the data types, whose key is the name of the data type and whose value is the data type itself.
     */
    protected Map<String, ToscaDataTypeDefinition> parseDataTypes(Map<String, Object> yamlRoot) {
        Map<String, Object> dataTypesRaw = ToscaYamlHelper.asMap(yamlRoot.get(DATA_TYPES_KEY));
        logger.info("Parsing the following data types: {}.", dataTypesRaw::keySet);
        return dataTypesRaw.entrySet().stream().map(dataTypeEntry -> {
            String dataTypeName = dataTypeEntry.getKey();
            Map<String, Object> dataTypeBody = ToscaYamlHelper.asMap(dataTypeEntry.getValue());
            Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) parseField(dataTypeBody.get(PROPERTIES_KEY), TypeOfToscaField.PROPERTY, null);
            return new ToscaDataTypeDefinition(dataTypeName, propertyDefinitions);
        }).collect(Collectors.toMap(ToscaDataTypeDefinition::getName, (dataType) -> dataType));
    }

    /**
     * Parses a node type definition.
     * @param yamlRoot The root of the YAML node type definition file.
     * @param dataTypes Available data types.
     * @return a {@link ToscaNodeType} representing the node type.
     */
    protected ToscaNodeType parseNodeType(Map<String, Object> yamlRoot, Map<String, ToscaDataTypeDefinition> dataTypes) {
        Map.Entry<String, Object> nodeTypeRaw = ToscaYamlHelper.asMap(yamlRoot.get(NODE_TYPES_KEY)).entrySet().iterator().next();
        String nodeTypeName = nodeTypeRaw.getKey();
        logger.info("Parsing the following node type: [{}].", nodeTypeName);
        Map<String, Object> nodeTypeBody = ToscaYamlHelper.asMap(nodeTypeRaw.getValue());
        Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) parseField(nodeTypeBody.get(PROPERTIES_KEY), TypeOfToscaField.PROPERTY, dataTypes);
        Map<String, ToscaAttributeDefinition> attributeDefinitions = (Map<String, ToscaAttributeDefinition>) parseField(nodeTypeBody.get(NODE_TYPES_ATTRIBUTES_KEY), TypeOfToscaField.ATTRIBUTE, dataTypes);
        ToscaNodeType nodeType = new ToscaNodeType(nodeTypeName, propertyDefinitions, attributeDefinitions);
        logger.info("Successfully parsed the following node type: [{}].", nodeType::toString);
        return nodeType;
    }

    /**
     * Parses a field (property or attribute) of a TOSCA resource.
     * @param rawFields The body of the field. Example: "{ name: { type: string, description: Name } }"
     * @param typeOfToscaField The type of the field that will be parsed.
     * @param dataTypes Available data types. If null, then the type of the fields must be a primitive or a collection.
     * @return a map of {@link ToscaFieldDefinition} representing the fields, whose key is the name of the field and whose value is the field itself.
     */
    protected Map<String, ? extends ToscaFieldDefinition> parseField(Object rawFields, TypeOfToscaField typeOfToscaField, Map<String, ToscaDataTypeDefinition> dataTypes) {
        Map<String, Object> fields = ToscaYamlHelper.asMap(rawFields);
        logger.debug("Parsing the following {}: {}.",
                () -> typeOfToscaField == TypeOfToscaField.ATTRIBUTE ? "attributes" : "properties", fields::keySet);

        return fields.entrySet().stream().map((field) -> {
            String name = field.getKey();
            Map<String, Object> fieldBody = ToscaYamlHelper.asMap(field.getValue());
            String description = ToscaYamlHelper.asString(fieldBody.get(FIELDS_DESCRIPTION_KEY));
            ToscaTypeDefinition type = parseType(fieldBody, dataTypes);

            if (typeOfToscaField == TypeOfToscaField.ATTRIBUTE) {
                ToscaAttributeDefinition attributeDefinition = new ToscaAttributeDefinition(name, description, type);
                logger.debug("Successfully parsed the following attribute: [{}].", attributeDefinition::toString);
                return attributeDefinition;
            }

            boolean required = ToscaYamlHelper.asBoolean(fieldBody.get(FIELDS_REQUIRED_KEY));
            ToscaFunction.ToscaBooleanFunction validation = parseToscaBooleanFunction(ToscaYamlHelper.asMap(fieldBody.get(FIELDS_VALIDATION_KEY)));
            ToscaPropertyDefinition propertyDefinition = new ToscaPropertyDefinition(name, description, type, required, validation);
            logger.debug("Successfully parsed the following property: [{}].", propertyDefinition::toString);
            return propertyDefinition;
        }).collect(Collectors.toMap(ToscaFieldDefinition::getName, (field) -> field));
    }

    /**
     * Parses the type of TOSCA resource. Types can be primitive, collection, or data type, and are represented by the {@link ToscaTypeDefinition} class.
     * @param typeBody The body of the type. Example: "{ type: list, entry_schema: { type: NodeOffering } }"
     * @param dataTypes The available data types. If null, then the type being parsed must be either a primitive or a collection.
     * @return a {@link ToscaTypeDefinition} representing the type of the field. Null if the type is not recognized.
     */
    protected ToscaTypeDefinition parseType(Map<String, Object> typeBody, Map<String, ToscaDataTypeDefinition> dataTypes) {
        String rawType = ToscaYamlHelper.asString(typeBody.get(FIELDS_TYPE_KEY));
        logger.debug("Parsing the following type: [{}].", rawType);
        ToscaPrimitiveType primitiveType = EnumUtils.getEnumIgnoreCase(ToscaPrimitiveType.class, rawType);
        if (primitiveType != null) {
            logger.debug("The type is a primitive, returning its corresponding ToscaTypeDefinition.");
            return ToscaTypeDefinition.ofPrimitive(primitiveType);
        }

        ToscaCollectionType collectionType = EnumUtils.getEnumIgnoreCase(ToscaCollectionType.class, rawType);
        if (collectionType != null) {
            logger.debug("The type is a collection, returning its corresponding ToscaTypeDefinition.");
            return ToscaTypeDefinition.ofCollection(collectionType, parseType(ToscaYamlHelper.asMap(typeBody.get(FIELDS_ENTRY_SCHEMA_KEY)), dataTypes));
        }

        if (MapUtils.isEmpty(dataTypes) || !dataTypes.containsKey(rawType)) {
            logger.debug("The [{}] type is not recognized, returning null.", rawType);
            return null;
        }

        logger.debug("The type is a data type, returning its corresponding ToscaTypeDefinition based in the declared data types: {}.", dataTypes::keySet);
        return ToscaTypeDefinition.ofDataType(dataTypes.get(rawType));
    }

    /**
     * Parses a TOSCA boolean ({@link ToscaFunction.ToscaBooleanFunction}) function.
     * @param validationBody The body of the validation field. Example: "{ $valid_values: [ $value, [ CloudManaged, ExternalManaged ] ] }")
     * @return a {@link ToscaFunction.ToscaBooleanFunction} representing the boolean function. If the function is not recognized, returns null.
     */
    protected ToscaFunction.ToscaBooleanFunction parseToscaBooleanFunction(Map<String, Object> validationBody) {
        if (MapUtils.isEmpty(validationBody)) {
            return null;
        }

        Map.Entry<String, Object> function = validationBody.entrySet().iterator().next();
        String name = function.getKey();
        Object arguments = function.getValue();
        logger.debug("Parsing the following TOSCA boolean function: [{}].", name);
        switch (name) {
            case "$valid_values":
                return parseValidValuesFunction(arguments);
        }
        return null;
    }

    /**
     * Parses the $valid_values TOSCA function.
     * @param arguments The arguments of the $valid_values function. Example: "{ $valid_values: [ $value, [ CloudManaged, ExternalManaged ] ] }"
     * @return a {@link ToscaBooleanFunctions.ValidValues} (typed as {@link ToscaFunction.ToscaBooleanFunction}) representing the $valid_values function.
     */
    private ToscaFunction.ToscaBooleanFunction parseValidValuesFunction(Object arguments) {
        List<Object> args = (List<Object>) arguments;
        List<Object> validValues = (List<Object>) args.get(1);
        return new ToscaBooleanFunctions.ValidValues(validValues);
    }

    public ToscaServiceTemplate parseServiceTemplate(String content, Map<String, ToscaNodeType> toscaProfile, Account caller) {
        Map<String, Object> rawToscaTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(content));
        ToscaServiceTemplateParsingContext context = new ToscaServiceTemplateParsingContext(toscaProfile);

        checkRootServiceTemplateYamlKeys(rawToscaTemplate, context);

        Map<String, Object> rawServiceTemplate = ToscaYamlHelper.asMap(rawToscaTemplate.get(SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY));
        Map<String, ToscaInputDefinition> inputs = parseInputs(ToscaYamlHelper.asMap(rawServiceTemplate.get(SERVICE_TEMPLATE_INPUTS_KEY)), context);
        Map<String, ToscaNodeTemplate> nodeTemplates = parseNodeTemplates(ToscaYamlHelper.asMap(rawServiceTemplate.get(SERVICE_TEMPLATE_INPUTS_KEY)), context);

        checkUnresolvedPropertiesByGetProperty(nodeTemplates, context);
        Map<String, Set<ToscaNodeTemplate>> serviceTemplateDependencies = buildGraphDependencies(nodeTemplates, context);

        if (context.hasErrors()) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }

        return new ToscaServiceTemplate();
    }

    private void checkUnresolvedPropertiesByGetProperty(Map<String, ToscaNodeTemplate> nodeTemplates, ToscaServiceTemplateParsingContext context) {
        for (Map.Entry<String, Set<ToscaProperty>> entry : context.getUnresolvedByGetProperty().entrySet()) {
            String node = entry.getKey();
            Set<ToscaProperty> unresolvedProperties = entry.getValue();

            unresolvedProperties.forEach(property -> {
                Map<String, Object> functionCall = ToscaYamlHelper.asMap(property.getRawValue());
                List<?> arguments = (List<?>) functionCall.get("$get_property");
                String targetNode = ToscaYamlHelper.asString(arguments.get(0));
                String targetProperty = ToscaYamlHelper.asString(arguments.get(1));
                if (!nodeTemplates.containsKey(targetNode)) {
                    return;
                }

                ToscaNodeTemplate targetNodeTemplate = nodeTemplates.get(targetNode);
                if (!targetNodeTemplate.hasProperty(targetProperty)) {
                    context.addError(String.format("The node [%s] references an invalid property of the node [%s].", node, targetNode), "node templates section");
                    return;
                }

                ToscaPropertyDefinition targetPropertyDefinition = targetNodeTemplate.getProperty(targetProperty).getDefinition();
                if (!property.getDefinition().getType().isAssignableFrom(targetPropertyDefinition.getType())) {
                    context.addError(String.format("Unmatching types between [node: %s, property: %s] and [node: %s, property: %s].", node, property.getDefinition().getName(), targetNode, targetProperty), "node templates section");
                }

                context.addNodeDependency(node, targetNode);
            });
        }
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
                }

                serviceTemplateDependencies.computeIfAbsent(node, (n) -> new HashSet<>())
                        .add(nodeTemplates.get(dependency));
            });
        }
        return serviceTemplateDependencies;
    }

    private void checkRootServiceTemplateYamlKeys(Map<String, Object> serviceTemplate, ToscaServiceTemplateParsingContext context) {
        validateKnownToscaKeys(serviceTemplate, Set.of(SERVICE_TEMPLATE_TOSCA_VERSION_KEY, SERVICE_TEMPLATE_DESCRIPTION_KEY, SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY), "YAML's root level", context);
        validateRequiredToscaKeys(serviceTemplate, Set.of(SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY), "YAML's root level", context);
        if (context.hasErrors()) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }

        validateKnownToscaKeys(serviceTemplate, Set.of(SERVICE_TEMPLATE_TOSCA_VERSION_KEY, SERVICE_TEMPLATE_DESCRIPTION_KEY, SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY), "YAML's root level", context);
        validateRequiredToscaKeys(serviceTemplate, Set.of(SERVICE_TEMPLATE_NODE_TEMPLATES_KEY, SERVICE_TEMPLATE_INPUTS_KEY), "Service template level", context);
        if (context.hasErrors()) {
            throw new InvalidParameterValueException(context.buildErrorMessages());
        }
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

    private ToscaNodeTemplate parseNodeTemplate(String name, Map<String, Object> body, ToscaServiceTemplateParsingContext context) {
        String nodeTypeName = ToscaYamlHelper.asString(body.get(FIELDS_TYPE_KEY));
        if (nodeTypeName == null || !context.getProfile().containsKey(nodeTypeName)) {
            context.addError(String.format("The node type [%s] is not valid.", nodeTypeName), "node templates section");
            return null;
        }

        ToscaNodeType nodeType = context.getProfile().get(nodeTypeName);
        validateKnownToscaKeys(body, Set.of(PROPERTIES_KEY, NODE_TEMPLATES_REQUIREMENTS_KEY), "node templates section", context);
        Map<String, ToscaProperty> properties = parseNodeTemplateProperties(name, ToscaYamlHelper.asMap(body.get(PROPERTIES_KEY)), nodeType, context);
        return new ToscaNodeTemplate(name, nodeType, properties);
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

    private Map<String, ToscaInputDefinition> parseInputs(Map<String, Object> inputs, ToscaServiceTemplateParsingContext context) {
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
        validateKnownToscaKeys(body, Set.of(FIELDS_DESCRIPTION_KEY, FIELDS_TYPE_KEY, FIELDS_VALIDATION_KEY, FIELDS_ENTRY_DEFAULT_VALUE_KEY), "inputs section", context);

        ToscaTypeDefinition type = parseType(body, null);
        if (type == null) {
            context.addError(String.format("The type of the input [%s] is not valid.", name), "inputs section");
            return null;
        }

        Object defaultValue = body.get(FIELDS_ENTRY_DEFAULT_VALUE_KEY);
        if (defaultValue != null && !type.isCompatibleWith(defaultValue)) {
            context.addError(String.format("The provided default value [%s] for the input [%s] is not compatible with the input type [%s].", defaultValue, name, type), "inputs section");
            return null;
        }

        Map<String, Object> rawValidation = ToscaYamlHelper.asMap(body.get(FIELDS_VALIDATION_KEY));
        ToscaFunction.ToscaBooleanFunction validation = parseToscaBooleanFunction(rawValidation);
        if (MapUtils.isNotEmpty(rawValidation) && validation == null) {
            context.addError(String.format("The validation function of the input [%s] is not valid.", name), "inputs section");
            return null;
        }

        String description = ToscaYamlHelper.asString(body.get(FIELDS_DESCRIPTION_KEY));
        return new ToscaInputDefinition(name, description, type, defaultValue, validation);
    }

    private void validateRequiredToscaKeys(Map<String, Object> content, Set<String> requiredKeys, String templateSection, ToscaServiceTemplateParsingContext context) {
        requiredKeys.stream()
                .filter(key -> !content.containsKey(key))
                .forEach(key -> context.addError(String.format("The key [%s] is required in the [%s] section.", key, templateSection), templateSection));
    }

    private void validateKnownToscaKeys(Map<String, Object> content, Set<String> validKeys, String templateSection, ToscaServiceTemplateParsingContext context) {
        content.keySet().stream()
                .filter(key -> !validKeys.contains(key))
                .forEach(key -> context.addError(String.format("Unknown key [%s] in the [%s] section.", key, templateSection), templateSection));
    }
}

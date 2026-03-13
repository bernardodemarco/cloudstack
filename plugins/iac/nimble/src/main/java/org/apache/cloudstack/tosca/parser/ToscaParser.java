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

import org.apache.cloudstack.tosca.functions.ToscaBooleanFunctions;
import org.apache.cloudstack.tosca.functions.ToscaFunction;
import org.apache.cloudstack.tosca.model.ToscaAttributeDefinition;
import org.apache.cloudstack.tosca.model.ToscaCollectionType;
import org.apache.cloudstack.tosca.model.ToscaDataTypeDefinition;
import org.apache.cloudstack.tosca.model.ToscaFieldDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ToscaParser {
    private final Logger logger = LogManager.getLogger(ToscaParser.class);

    protected enum TypeOfToscaField {
        ATTRIBUTE, PROPERTY
    }

    private static final String DATA_TYPES_KEY = "data_types";
    private static final String NODE_TYPES_KEY = "node_types";

    private static final String NODE_TYPES_ATTRIBUTES_KEY = "attributes";
    private static final String NODE_TYPES_PROPERTIES_KEY = "properties";

    private static final String FIELDS_TYPE_KEY = "type";
    private static final String FIELDS_REQUIRED_KEY = "required";
    private static final String FIELDS_VALIDATION_KEY = "validation";
    private static final String FIELDS_DESCRIPTION_KEY = "description";
    private static final String FIELDS_ENTRY_SCHEMA_KEY = "entry_schema";

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
            Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) parseField(dataTypeBody.get(NODE_TYPES_PROPERTIES_KEY), TypeOfToscaField.PROPERTY, null);
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
        Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) parseField(nodeTypeBody.get(NODE_TYPES_PROPERTIES_KEY), TypeOfToscaField.PROPERTY, dataTypes);
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
}

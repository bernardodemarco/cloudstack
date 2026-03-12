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
import org.apache.commons.lang3.EnumUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ToscaParser {
    private final Logger logger = LogManager.getLogger(ToscaParser.class);

    private enum FieldDefinitionType {
        ATTRIBUTE, PROPERTY
    }

    private static final String NODE_TYPES_KEY = "node_types";
    private static final String DATA_TYPES_KEY = "data_types";

    private static final String NODE_TYPES_PROPERTIES_KEY = "properties";
    private static final String NODE_TYPES_ATTRIBUTES_KEY = "attributes";

    private static final String FIELDS_DESCRIPTION_KEY = "description";
    private static final String FIELDS_TYPE_KEY = "type";
    private static final String FIELDS_VALIDATION_KEY = "validation";
    private static final String FIELDS_REQUIRED_KEY = "required";
    private static final String FIELDS_ENTRY_SCHEMA_KEY = "entry_schema";

    public ToscaNodeType parseNodeTypeDefinitionFile(String nodeTypeContent) {
        Object rawYaml = ToscaYamlHelper.loadYaml(nodeTypeContent);
        Map<String, Object> yamlRoot = ToscaYamlHelper.asMap(rawYaml);
        Map<String, ToscaDataTypeDefinition> dataTypes = parseDataTypes(yamlRoot);
        return parseNodeType(yamlRoot, dataTypes);
    }

    private Map<String, ToscaDataTypeDefinition> parseDataTypes(Map<String, Object> yamlRoot) {
        Map<String, Object> dataTypesRaw = ToscaYamlHelper.asMap(yamlRoot.get(DATA_TYPES_KEY));
        logger.info("Parsing the following data types: {}.", dataTypesRaw::keySet);
        return dataTypesRaw.entrySet().stream().map(dataTypeEntry -> {
            String dataTypeName = dataTypeEntry.getKey();
            Map<String, Object> dataTypeBody = ToscaYamlHelper.asMap(dataTypeEntry.getValue());
            Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) parseFieldDefinition(dataTypeBody.get(NODE_TYPES_PROPERTIES_KEY), FieldDefinitionType.PROPERTY, null);
            return new ToscaDataTypeDefinition(dataTypeName, propertyDefinitions);
        }).collect(Collectors.toMap(ToscaDataTypeDefinition::getName, (dataType) -> dataType));
    }

    private ToscaNodeType parseNodeType(Map<String, Object> yamlRoot, Map<String, ToscaDataTypeDefinition> dataTypes) {
        Map.Entry<String, Object> nodeTypeRaw = ToscaYamlHelper.asMap(yamlRoot.get(NODE_TYPES_KEY)).entrySet().iterator().next();
        String nodeTypeName = nodeTypeRaw.getKey();
        logger.info("Parsing the following node type: [{}].", nodeTypeName);
        Map<String, Object> nodeTypeBody = ToscaYamlHelper.asMap(nodeTypeRaw.getValue());
        Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) parseFieldDefinition(nodeTypeBody.get(NODE_TYPES_PROPERTIES_KEY), FieldDefinitionType.PROPERTY, dataTypes);
        Map<String, ToscaAttributeDefinition> attributeDefinitions = (Map<String, ToscaAttributeDefinition>) parseFieldDefinition(nodeTypeBody.get(NODE_TYPES_ATTRIBUTES_KEY), FieldDefinitionType.ATTRIBUTE, dataTypes);
        ToscaNodeType nodeType = new ToscaNodeType(nodeTypeName, propertyDefinitions, attributeDefinitions);
        logger.info("Successfully parsed the following node type: [{}].", nodeType::toString);
        return nodeType;
    }

    private Map<String, ? extends ToscaFieldDefinition> parseFieldDefinition(Object rawFields, FieldDefinitionType fieldDefinitionType, Map<String, ToscaDataTypeDefinition> dataTypes) {
        Map<String, Object> fields = ToscaYamlHelper.asMap(rawFields);
        logger.debug("Parsing the following {}: {}.",
                () -> fieldDefinitionType == FieldDefinitionType.ATTRIBUTE ? "attributes" : "properties", fields::keySet);

        return fields.entrySet().stream().map((field) -> {
            String fieldName = field.getKey();
            Map<String, Object> fieldBody = ToscaYamlHelper.asMap(field.getValue());
            String fieldDescription = ToscaYamlHelper.asString(fieldBody.get(FIELDS_DESCRIPTION_KEY));
            ToscaTypeDefinition fieldType = parseFieldType(fieldBody, dataTypes);

            if (fieldDefinitionType == FieldDefinitionType.ATTRIBUTE) {
                ToscaAttributeDefinition attributeDefinition = new ToscaAttributeDefinition(fieldName, fieldDescription, fieldType);
                logger.debug("Successfully parsed the following attribute: [{}].", attributeDefinition::toString);
                return attributeDefinition;
            }

            boolean required = ToscaYamlHelper.asBoolean(fieldBody.get(FIELDS_REQUIRED_KEY));
            ToscaFunction.ToscaBooleanFunction validation = parseToscaBooleanFunction(ToscaYamlHelper.asMap(fieldBody.get(FIELDS_VALIDATION_KEY)));
            ToscaPropertyDefinition propertyDefinition = new ToscaPropertyDefinition(fieldName, fieldDescription, fieldType, required, validation);
            logger.debug("Successfully parsed the following property: [{}].", propertyDefinition::toString);
            return propertyDefinition;
        }).collect(Collectors.toMap(ToscaFieldDefinition::getName, (field) -> field));
    }

    private ToscaTypeDefinition parseFieldType(Map<String, Object> fieldBody, Map<String, ToscaDataTypeDefinition> dataTypes) {
        String rawType = ToscaYamlHelper.asString(fieldBody.get(FIELDS_TYPE_KEY));
        logger.debug("Parsing the following type: [{}].", rawType);
        ToscaPrimitiveType primitiveType = EnumUtils.getEnumIgnoreCase(ToscaPrimitiveType.class, rawType);
        if (primitiveType != null) {
            logger.debug("The type is a primitive, returning its corresponding ToscaTypeDefinition.");
            return ToscaTypeDefinition.ofPrimitive(primitiveType);
        }

        ToscaCollectionType collectionType = EnumUtils.getEnumIgnoreCase(ToscaCollectionType.class, rawType);
        if (collectionType != null) {
            logger.debug("The type is a collection, returning its corresponding ToscaTypeDefinition.");
            return ToscaTypeDefinition.ofCollection(collectionType, parseFieldType(ToscaYamlHelper.asMap(fieldBody.get(FIELDS_ENTRY_SCHEMA_KEY)), dataTypes));
        }

        logger.debug("The type is a data type, returning its corresponding ToscaTypeDefinition based in the declared data types: {}.", dataTypes::keySet);
        return ToscaTypeDefinition.ofDataType(dataTypes.get(rawType));
    }

    private ToscaFunction.ToscaBooleanFunction parseToscaBooleanFunction(Map<String, Object> validationBody) {
        Map.Entry<String, Object> function = validationBody.entrySet().iterator().next();
        String name = function.getKey();
        Object body = function.getValue();
        logger.debug("Parsing the following TOSCA boolean function: [{}].", name);
        switch (name) {
            case "$valid_values":
                return parseValidValuesFunction(body);
        }
        return null;
    }

    private ToscaFunction.ToscaBooleanFunction parseValidValuesFunction(Object functionBody) {
        List<Object> arguments = (List<Object>) functionBody;
        List<Object> validValues = (List<Object>) arguments.get(1);
        return new ToscaBooleanFunctions.ValidValues(validValues);
    }
}

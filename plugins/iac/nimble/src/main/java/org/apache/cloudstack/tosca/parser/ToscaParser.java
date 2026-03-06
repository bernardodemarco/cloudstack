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

import org.apache.cloudstack.tosca.model.ToscaAttributeDefinition;
import org.apache.cloudstack.tosca.model.ToscaFieldDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ToscaParser {
    private final Logger logger = LogManager.getLogger(ToscaParser.class);

    enum FieldDefinitionType {
        ATTRIBUTE, PROPERTY
    }

    private static final String NODE_TYPES_KEY = "node_types";
    private static final String NODE_TYPES_PROPERTIES_KEY = "properties";
    private static final String NODE_TYPES_ATTRIBUTES_KEY = "attributes";

    @SuppressWarnings("unchecked")
    public ToscaNodeType parseNodeType(String nodeTypeName, String nodeTypeContent) {
        Object rawYaml = ToscaYamlHelper.loadYaml(nodeTypeContent);
        Map<String, Object> yamlRoot = ToscaYamlHelper.asMap(rawYaml);
        logger.debug("Trying to extract only one node type from the [{}] YAML definition file, since it is expected for each node type to be declared separately.", nodeTypeName);
        Optional<Map.Entry<String, Object>> nodeTypeRaw = ToscaYamlHelper.asMap(yamlRoot.get(NODE_TYPES_KEY)).entrySet().stream().findFirst();
        if (nodeTypeRaw.isEmpty()) {
            logger.error("No node types are declared in the [{}] YAML definition file.", nodeTypeName);
            return null;
        }
        Map<String, Object> nodeTypeBody = ToscaYamlHelper.asMap(nodeTypeRaw.get().getValue());
        Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) parseFieldDefinition(nodeTypeBody.get(NODE_TYPES_PROPERTIES_KEY), FieldDefinitionType.PROPERTY);
        Map<String, ToscaAttributeDefinition> attributeDefinitions = (Map<String, ToscaAttributeDefinition>) parseFieldDefinition(nodeTypeBody.get(NODE_TYPES_ATTRIBUTES_KEY), FieldDefinitionType.ATTRIBUTE);
        ToscaNodeType nodeType = new ToscaNodeType(nodeTypeName, propertyDefinitions, attributeDefinitions);
        logger.info("Successfully parsed the following node type: [{}].", nodeType::toString);
        return nodeType;
    }

    private Map<String, ? extends ToscaFieldDefinition> parseFieldDefinition(Object rawFields, FieldDefinitionType fieldDefinitionType) {
        Map<String, Object> fields = ToscaYamlHelper.asMap(rawFields);
        logger.debug("Parsing the following {}: {}.",
                () -> fieldDefinitionType == FieldDefinitionType.ATTRIBUTE ? "attributes" : "properties", fields::keySet);

        return fields.entrySet().stream().map((field) -> {
            String fieldName = field.getKey();
            Map<String, Object> fieldBody = ToscaYamlHelper.asMap(field.getValue());
            ToscaPrimitiveType fieldType = EnumUtils.getEnumIgnoreCase(ToscaPrimitiveType.class, ToscaYamlHelper.asString(fieldBody.get("type")));
            String fieldDescription = ToscaYamlHelper.asString(fieldBody.get("description"));

            if (fieldDefinitionType == FieldDefinitionType.ATTRIBUTE) {
                ToscaAttributeDefinition attributeDefinition = new ToscaAttributeDefinition(fieldName, fieldDescription, fieldType);
                logger.debug("Successfully parsed the following attribute: [{}].", attributeDefinition::toString);
                return attributeDefinition;
            }

            boolean required = BooleanUtils.toBoolean(ToscaYamlHelper.asString(fieldBody.get("required")));
            Object validation = fieldBody.get("validation");
            ToscaPropertyDefinition propertyDefinition = new ToscaPropertyDefinition(fieldName, fieldDescription, fieldType, required, validation);
            logger.debug("Successfully parsed the following property: [{}].", propertyDefinition::toString);
            return propertyDefinition;
        }).collect(Collectors.toMap(ToscaFieldDefinition::getName, (field) -> field));
    }
}

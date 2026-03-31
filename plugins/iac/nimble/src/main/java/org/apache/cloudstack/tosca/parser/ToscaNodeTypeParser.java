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

import com.cloud.utils.Pair;
import org.apache.cloudstack.tosca.model.ToscaAttributeDefinition;
import org.apache.cloudstack.tosca.model.ToscaDataTypeDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.Map;
import java.util.stream.Collectors;

public class ToscaNodeTypeParser {
    private final Logger logger = LogManager.getLogger(ToscaNodeTypeParser.class);

    private final ToscaFieldParser toscaFieldParser;

    @Inject
    public ToscaNodeTypeParser(ToscaFieldParser toscaFieldParser) {
        this.toscaFieldParser = toscaFieldParser;
    }

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
        Map<String, Object> dataTypesRaw = ToscaYamlHelper.asMap(yamlRoot.get(ToscaConstants.DATA_TYPES));
        logger.info("Parsing the following data types: {}.", dataTypesRaw::keySet);
        return dataTypesRaw.entrySet().stream().map(dataTypeEntry -> {
            String dataTypeName = dataTypeEntry.getKey();
            Map<String, Object> dataTypeBody = ToscaYamlHelper.asMap(dataTypeEntry.getValue());
            Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) toscaFieldParser.parseField(dataTypeBody.get(ToscaConstants.PROPERTIES), ToscaFieldParser.TypeOfToscaField.PROPERTY, null);
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
        Map.Entry<String, Object> nodeTypeRaw = ToscaYamlHelper.asMap(yamlRoot.get(ToscaConstants.NODE_TYPES)).entrySet().iterator().next();
        String nodeTypeName = nodeTypeRaw.getKey();
        logger.info("Parsing the following node type: [{}].", nodeTypeName);
        Map<String, Object> nodeTypeBody = ToscaYamlHelper.asMap(nodeTypeRaw.getValue());
        Pair<String, String> nodeTypeApis = parseNodeTypeMetadata(nodeTypeBody.get(ToscaConstants.METADATA));
        Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) toscaFieldParser.parseField(nodeTypeBody.get(ToscaConstants.PROPERTIES), ToscaFieldParser.TypeOfToscaField.PROPERTY, dataTypes);
        Map<String, ToscaAttributeDefinition> attributeDefinitions = (Map<String, ToscaAttributeDefinition>) toscaFieldParser.parseField(nodeTypeBody.get(ToscaConstants.ATTRIBUTES), ToscaFieldParser.TypeOfToscaField.ATTRIBUTE, dataTypes);
        ToscaNodeType nodeType = new ToscaNodeType(nodeTypeName, propertyDefinitions, attributeDefinitions, nodeTypeApis.first(), nodeTypeApis.second());
        logger.info("Successfully parsed the following node type: [{}].", nodeType::toString);
        return nodeType;
    }

    /**
     * Parses the metadata of a node type.
     * @param metadataBody The metadata body of the node type. Current supported fields are "provisioning_api" and "rollback_api".
     * @return A pair of the provisioning API name and the rollback API name.
     */
    private Pair<String, String> parseNodeTypeMetadata(Object metadataBody) {
        Map<String, Object> metadata = ToscaYamlHelper.asMap(metadataBody);
        String provisioningApiName = ToscaYamlHelper.asString(metadata.get(ToscaConstants.PROVISIONING_API));
        String rollBackApiName = ToscaYamlHelper.asString(metadata.get(ToscaConstants.ROLLBACK_API));
        return new Pair<>(provisioningApiName, rollBackApiName);
    }
}

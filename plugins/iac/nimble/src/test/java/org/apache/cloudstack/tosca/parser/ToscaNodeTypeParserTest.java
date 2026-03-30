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
import org.apache.cloudstack.tosca.model.ToscaDataTypeDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ToscaNodeTypeParserTest {
    private ToscaNodeTypeParser toscaNodeTypeParserSpy;

    @Before
    public void setUp() {
        ToscaFieldParser toscaFieldParser = new ToscaFieldParser();
        toscaNodeTypeParserSpy = Mockito.spy(new ToscaNodeTypeParser(toscaFieldParser));
    }

    @Test
    public void parseNodeTypeDefinitionFileTestEnsureNodeTypeIsParsedConsideringTheAvailableDataTypes() {
        String nodeTypeContent = "{data_types: {NameValueMapping: {properties: {name: {type: string, required: true}, value: {type: string, required: true}}}}, node_types: {MockType: {attributes: {id: {type: string, description: ID.}}, properties: {name-value: {type: NameValueMapping}}}}}";
        ToscaNodeType nodeType = toscaNodeTypeParserSpy.parseNodeTypeDefinitionFile(nodeTypeContent);
        Assert.assertEquals("MockType", nodeType.getName());
        Assert.assertEquals(1, nodeType.getProperties().size());
        Assert.assertEquals(1, nodeType.getAttributes().size());
    }

    @Test
    public void parseDataTypesTestEnsureDataTypesAreSuccessfullyParsed() {
        String dataTypeContent = "{data_types: {NameValueMapping: {properties: {name: {type: string, required: true}, value: {type: string, required: true}}}}}";
        Map<String, ToscaDataTypeDefinition> dataTypes = toscaNodeTypeParserSpy.parseDataTypes(ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(dataTypeContent)));
        Assert.assertEquals(1, dataTypes.size());
        Assert.assertEquals("NameValueMapping", dataTypes.get("NameValueMapping").getName());
        Assert.assertEquals(2, dataTypes.get("NameValueMapping").getProperties().size());
    }

    @Test
    public void parseNodeTypeTestEnsureToscaNodeTypeDefinitionFileIsSuccessfullyParsedAlongWithItsPropertiesAndAttributes() {
        String nodeTypeName = "MockType";
        String nodeTypeContent = "{tosca_definitions_version: tosca_2_0, description: Mock node type definition, node_types: {MockType: {metadata: {provisioning-api: createApi, rollback-api: deleteApi}, description: Apache CloudStack MockType node type., attributes: {id: {type: string, description: ID.}}, properties: {zone-id: {type: string, description: Zone ID., required: true}}}}}";

        ToscaNodeType nodeType = toscaNodeTypeParserSpy.parseNodeType(ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(nodeTypeContent)), null);
        Map<String, ToscaPropertyDefinition> properties = nodeType.getProperties();
        Map<String, ToscaAttributeDefinition> attributes = nodeType.getAttributes();
        Assert.assertEquals(nodeTypeName, nodeType.getName());
        Assert.assertTrue(attributes.containsKey("id"));
        Assert.assertTrue(properties.containsKey("zone-id"));
        Assert.assertEquals("createApi", nodeType.getProvisioningApi());
        Assert.assertEquals("deleteApi", nodeType.getRollbackApi());
    }
}
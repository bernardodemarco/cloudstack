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
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ToscaParserTest {
    @Spy
    private ToscaParser toscaParserSpy;

    private List<ToscaPropertyDefinition> getExpectedToscaPropertyDefinitionsForTests() {
        return List.of(
                new ToscaPropertyDefinition("zone-id", "Zone ID.", ToscaPrimitiveType.STRING, true, null),
                new ToscaPropertyDefinition("disk-offering-id", "Disk offering ID.", ToscaPrimitiveType.STRING, false, "{$valid_values: [$value, [CloudManaged, ExternalManaged]]}"),
                new ToscaPropertyDefinition("amount", "Amount.", ToscaPrimitiveType.INTEGER, false, null)
        );
    }

    private List<ToscaAttributeDefinition> getExpectedToscaAttributeDefinitionsForTests() {
        return List.of(
                new ToscaAttributeDefinition("id", "ID.", ToscaPrimitiveType.STRING),
                new ToscaAttributeDefinition("name", "Name.", ToscaPrimitiveType.STRING)
        );
    }

    @Test
    public void parseNodeTypeTestEnsureToscaNodeTypeIsSuccessfullyParsedAlongWithItsPropertiesAndAttributes() {
        String nodeTypeName = "toscaNodeType";
        String nodeTypeContentWithMinifiedYaml = "{tosca_definitions_version: tosca_2_0, description: \"Mock node type definition.\\n\", node_types: {MockType: {description: \"Apache CloudStack MockType node type.\\n\", derived_from: Root, attributes: {id: {type: string, description: ID.}, name: {type: string, description: Name.}}, properties: {zone-id: {type: string, description: Zone ID., required: true}, amount: {type: integer, description: Amount.}, disk-offering-id: {type: string, description: Disk offering ID., required: false, validation: {$valid_values: [$value, [1, 2]]}}}}}}\n";

        ToscaNodeType nodeType = toscaParserSpy.parseNodeType(nodeTypeName, nodeTypeContentWithMinifiedYaml);
        Map<String, ToscaPropertyDefinition> properties = nodeType.getProperties();
        Map<String, ToscaAttributeDefinition> attributes = nodeType.getAttributes();
        Assert.assertEquals(nodeTypeName, nodeType.getName());

        List<ToscaPropertyDefinition> expectedProperties = getExpectedToscaPropertyDefinitionsForTests();
        Assert.assertEquals(expectedProperties.size(), properties.size());
        for (ToscaPropertyDefinition expectedProperty : expectedProperties) {
            ToscaPropertyDefinition actualProperty = properties.get(expectedProperty.getName());
            Assert.assertEquals(expectedProperty.getName(), actualProperty.getName());
            Assert.assertEquals(expectedProperty.getDescription(), actualProperty.getDescription());
            Assert.assertEquals(expectedProperty.getType(), actualProperty.getType());
            Assert.assertEquals(expectedProperty.isRequired(), actualProperty.isRequired());
//            Assert.assertEquals(ToscaYamlHelper.asString(expectedProperty.getValidation()), ToscaYamlHelper.asString(actualProperty.getValidation()));
        }

        List<ToscaAttributeDefinition> expectedAttributes = getExpectedToscaAttributeDefinitionsForTests();
        Assert.assertEquals(expectedAttributes.size(), attributes.size());
        for (ToscaAttributeDefinition expectedAttribute : expectedAttributes) {
            ToscaAttributeDefinition actualAttribute = attributes.get(expectedAttribute.getName());
            Assert.assertEquals(expectedAttribute.getName(), actualAttribute.getName());
            Assert.assertEquals(expectedAttribute.getType(), actualAttribute.getType());
            Assert.assertEquals(expectedAttribute.getDescription(), actualAttribute.getDescription());
        }
    }

    @Test
    public void parseNodeTypTestReturnNullWhenThereAreNoNodeTypesDeclaredInTheYamlContent() {
        String nodeTypeContentWithMinifiedYaml = "{tosca_definitions_version: tosca_2_0, description: \"Apache CloudStack TOSCA profile Volume node type definition.\\n\"}\n";
        Assert.assertNull(toscaParserSpy.parseNodeType("toscaNodeType", nodeTypeContentWithMinifiedYaml));
    }
}

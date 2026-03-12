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
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;
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

//    private List<ToscaPropertyDefinition> getExpectedToscaPropertyDefinitionsForTests() {
//        return List.of(
//                new ToscaPropertyDefinition("zone-id", "Zone ID.", ToscaPrimitiveType.STRING, true, null),
//                new ToscaPropertyDefinition("disk-offering-id", "Disk offering ID.", ToscaPrimitiveType.STRING, false, "{$valid_values: [$value, [CloudManaged, ExternalManaged]]}"),
//                new ToscaPropertyDefinition("amount", "Amount.", ToscaPrimitiveType.INTEGER, false, null)
//        );
//    }
//
//    private List<ToscaAttributeDefinition> getExpectedToscaAttributeDefinitionsForTests() {
//        return List.of(
//                new ToscaAttributeDefinition("id", "ID.", ToscaPrimitiveType.STRING),
//                new ToscaAttributeDefinition("name", "Name.", ToscaPrimitiveType.STRING)
//        );
//    }
//
//    @Test
//    public void parseNodeTypeTestEnsureToscaNodeTypeDefinitionFileIsSuccessfullyParsedAlongWithItsPropertiesAndAttributes() {
//        String nodeTypeName = "toscaNodeType";
//        String nodeTypeContentWithMinifiedYaml = "{tosca_definitions_version: tosca_2_0, description: \"Mock node type definition.\\n\", node_types: {MockType: {description: \"Apache CloudStack MockType node type.\\n\", derived_from: Root, attributes: {id: {type: string, description: ID.}, name: {type: string, description: Name.}}, properties: {zone-id: {type: string, description: Zone ID., required: true}, amount: {type: integer, description: Amount.}, disk-offering-id: {type: string, description: Disk offering ID., required: false, validation: {$valid_values: [$value, [1, 2]]}}}}}}\n";
//
//        ToscaNodeType nodeType = toscaParserSpy.parseNodeTypeDefinitionFile(nodeTypeName, nodeTypeContentWithMinifiedYaml);
//        Map<String, ToscaPropertyDefinition> properties = nodeType.getProperties();
//        Map<String, ToscaAttributeDefinition> attributes = nodeType.getAttributes();
//        Assert.assertEquals(nodeTypeName, nodeType.getName());
//
//        List<ToscaPropertyDefinition> expectedProperties = getExpectedToscaPropertyDefinitionsForTests();
//        Assert.assertEquals(expectedProperties.size(), properties.size());
//        for (ToscaPropertyDefinition expectedProperty : expectedProperties) {
//            ToscaPropertyDefinition actualProperty = properties.get(expectedProperty.getName());
//            Assert.assertEquals(expectedProperty.getName(), actualProperty.getName());
//            Assert.assertEquals(expectedProperty.getDescription(), actualProperty.getDescription());
//            Assert.assertEquals(expectedProperty.getType(), actualProperty.getType());
//            Assert.assertEquals(expectedProperty.isRequired(), actualProperty.isRequired());
////            Assert.assertEquals(ToscaYamlHelper.asString(expectedProperty.getValidation()), ToscaYamlHelper.asString(actualProperty.getValidation()));
//        }
//
//        List<ToscaAttributeDefinition> expectedAttributes = getExpectedToscaAttributeDefinitionsForTests();
//        Assert.assertEquals(expectedAttributes.size(), attributes.size());
//        for (ToscaAttributeDefinition expectedAttribute : expectedAttributes) {
//            ToscaAttributeDefinition actualAttribute = attributes.get(expectedAttribute.getName());
//            Assert.assertEquals(expectedAttribute.getName(), actualAttribute.getName());
//            Assert.assertEquals(expectedAttribute.getType(), actualAttribute.getType());
//            Assert.assertEquals(expectedAttribute.getDescription(), actualAttribute.getDescription());
//        }
//    }
//
//    @Test
//    public void parseNodeTypTestReturnNullWhenThereAreNoNodeTypesDeclaredInTheYamlContent() {
//        String nodeTypeContentWithMinifiedYaml = "{tosca_definitions_version: tosca_2_0, description: \"Apache CloudStack TOSCA profile Volume node type definition.\\n\"}\n";
//        Assert.assertNull(toscaParserSpy.parseNodeTypeDefinitionFile("toscaNodeType", nodeTypeContentWithMinifiedYaml));
//    }

    @Test
    public void parseToscaBooleanFunctionTestParseValidValuesFunction() {
        Object validationBody = ToscaYamlHelper.loadYaml("$valid_values: [ $value, [TCP, UDP, ICMP, ALL] ]");
        ToscaFunction.ToscaBooleanFunction function = toscaParserSpy.parseToscaBooleanFunction(ToscaYamlHelper.asMap(validationBody));
        Assert.assertTrue(function instanceof ToscaBooleanFunctions.ValidValues);
    }

    @Test
    public void parseFieldTypeTestParsePrimitiveTypes() {
        Object fieldBody = ToscaYamlHelper.loadYaml("{ type: string, description: ID. }");
        ToscaTypeDefinition type = toscaParserSpy.parseFieldType(ToscaYamlHelper.asMap(fieldBody), null);
        Assert.assertEquals(ToscaTypeDefinition.Kind.PRIMITIVE, type.getKind());
        Assert.assertEquals(ToscaPrimitiveType.STRING, type.getPrimitiveType());
    }

    @Test
    public void parseFieldTypeTestParseDataTypes() {
        Object fieldBody = ToscaYamlHelper.loadYaml("{ type: NameValueMapping }");
        Object dataTypeRaw =  ToscaYamlHelper.loadYaml("{data_types: {NameValueMapping: {properties: {name: {type: string, description: The name of the key-value pair., required: true}, value: {type: string, description: The value of the key-value pair., required: true}}}}}");
        Map<String, ToscaDataTypeDefinition> dataTypes = toscaParserSpy.parseDataTypes(ToscaYamlHelper.asMap(dataTypeRaw));
        ToscaTypeDefinition type = toscaParserSpy.parseFieldType(ToscaYamlHelper.asMap(fieldBody), dataTypes);
        Assert.assertEquals(ToscaTypeDefinition.Kind.DATA_TYPE, type.getKind());
        Assert.assertEquals(dataTypes.get("NameValueMapping").getName(), type.getDataType().getName());
        Assert.assertEquals(dataTypes.get("NameValueMapping").getProperties(), type.getDataType().getProperties());
    }

    @Test
    public void parseFieldTypeTestParseCollectionTypes() {
        Object fieldBody = ToscaYamlHelper.loadYaml("{ type: list, entry_schema: { type: NameValueMapping } }");
        Object dataTypeRaw =  ToscaYamlHelper.loadYaml("{data_types: {NameValueMapping: {properties: {name: {type: string, description: The name of the key-value pair., required: true}, value: {type: string, description: The value of the key-value pair., required: true}}}}}");
        Map<String, ToscaDataTypeDefinition> dataTypes = toscaParserSpy.parseDataTypes(ToscaYamlHelper.asMap(dataTypeRaw));
        ToscaTypeDefinition type = toscaParserSpy.parseFieldType(ToscaYamlHelper.asMap(fieldBody), dataTypes);
        Assert.assertEquals(ToscaTypeDefinition.Kind.COLLECTION, type.getKind());
        Assert.assertEquals(ToscaCollectionType.LIST, type.getCollectionType());
        Assert.assertEquals(dataTypes.get("NameValueMapping").getName(), type.getEntrySchema().getDataType().getName());
        Assert.assertEquals(dataTypes.get("NameValueMapping").getProperties(), type.getEntrySchema().getDataType().getProperties());
    }
}

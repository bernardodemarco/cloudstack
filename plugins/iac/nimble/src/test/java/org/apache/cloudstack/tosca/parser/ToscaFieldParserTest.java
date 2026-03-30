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
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ToscaFieldParserTest {
    private ToscaFieldParser toscaFieldParserSpy;

    private ToscaNodeTypeParser toscaNodeTypeParser;

    @Before
    public void setUp() {
        toscaFieldParserSpy = Mockito.spy(new ToscaFieldParser());
        toscaNodeTypeParser = new ToscaNodeTypeParser(toscaFieldParserSpy);
    }

    @Test
    public void parseFieldTestSuccessfullyParseProperties() {
        String properties = "{amount: {type: map, entry_schema: {type: NameValueMapping}, description: Amount.}, disk-offering-id: {type: string, description: Disk offering ID., required: true, validation: {$valid_values: [$value, [1, 2]]}, metadata: {api-parameter: diskofferingid}}}";
        Object dataTypeRaw = ToscaYamlHelper.loadYaml("{data_types: {NameValueMapping: {properties: {name: {type: string, required: true}, value: {type: string, required: true}}}}}");
        Map<String, ToscaDataTypeDefinition> dataTypes = toscaNodeTypeParser.parseDataTypes(ToscaYamlHelper.asMap(dataTypeRaw));

        Map<String, ToscaPropertyDefinition> propertyDefinitions = (Map<String, ToscaPropertyDefinition>) toscaFieldParserSpy.parseField(ToscaYamlHelper.loadYaml(properties), ToscaFieldParser.TypeOfToscaField.PROPERTY, dataTypes);
        Assert.assertEquals(2, propertyDefinitions.size());
        Assert.assertEquals("amount", propertyDefinitions.get("amount").getName());
        Assert.assertEquals("Amount.", propertyDefinitions.get("amount").getDescription());
        Assert.assertFalse(propertyDefinitions.get("amount").isRequired());
        Assert.assertEquals(ToscaCollectionType.MAP, propertyDefinitions.get("amount").getType().getCollectionType());
        Assert.assertEquals("NameValueMapping", propertyDefinitions.get("amount").getType().getEntrySchema().getDataType().getName());

        Assert.assertEquals("disk-offering-id", propertyDefinitions.get("disk-offering-id").getName());
        Assert.assertEquals("Disk offering ID.", propertyDefinitions.get("disk-offering-id").getDescription());
        Assert.assertTrue(propertyDefinitions.get("disk-offering-id").isRequired());
        Assert.assertEquals(ToscaPrimitiveType.STRING, propertyDefinitions.get("disk-offering-id").getType().getPrimitiveType());
        Assert.assertTrue(propertyDefinitions.get("disk-offering-id").getValidation() instanceof ToscaBooleanFunctions.ValidValues);
        Assert.assertEquals("diskofferingid", propertyDefinitions.get("disk-offering-id").getApiParameter());
    }

    @Test
    public void parseFieldTestSuccessfullyParseAttributes() {
        String attributes = "{id: {type: string, description: ID.}, name: {type: string, description: Name.}}";
        Map<String, ToscaAttributeDefinition> attributeDefinitions = (Map<String, ToscaAttributeDefinition>) toscaFieldParserSpy.parseField(ToscaYamlHelper.loadYaml(attributes), ToscaFieldParser.TypeOfToscaField.ATTRIBUTE, null);
        Assert.assertEquals(2, attributeDefinitions.size());
        Assert.assertTrue(attributeDefinitions.containsKey("id"));
        Assert.assertEquals("id", attributeDefinitions.get("id").getName());
        Assert.assertEquals("ID.", attributeDefinitions.get("id").getDescription());
        Assert.assertEquals(ToscaPrimitiveType.STRING, attributeDefinitions.get("id").getType().getPrimitiveType());

        Assert.assertTrue(attributeDefinitions.containsKey("name"));
        Assert.assertEquals("name", attributeDefinitions.get("name").getName());
        Assert.assertEquals("Name.", attributeDefinitions.get("name").getDescription());
        Assert.assertEquals(ToscaPrimitiveType.STRING, attributeDefinitions.get("name").getType().getPrimitiveType());
    }

    @Test
    public void parseTypeTestParsePrimitiveTypes() {
        Object fieldBody = ToscaYamlHelper.loadYaml("{ type: string, description: ID. }");
        ToscaTypeDefinition type = toscaFieldParserSpy.parseType(ToscaYamlHelper.asMap(fieldBody), null);
        Assert.assertEquals(ToscaTypeDefinition.Kind.PRIMITIVE, type.getKind());
        Assert.assertEquals(ToscaPrimitiveType.STRING, type.getPrimitiveType());
    }

    @Test
    public void parseTypeTestParseDataTypes() {
        Object fieldBody = ToscaYamlHelper.loadYaml("{ type: NameValueMapping }");
        Object dataTypeRaw =  ToscaYamlHelper.loadYaml("{data_types: {NameValueMapping: {properties: {name: {type: string, description: The name of the key-value pair., required: true}, value: {type: string, description: The value of the key-value pair., required: true}}}}}");
        Map<String, ToscaDataTypeDefinition> dataTypes = toscaNodeTypeParser.parseDataTypes(ToscaYamlHelper.asMap(dataTypeRaw));
        ToscaTypeDefinition type = toscaFieldParserSpy.parseType(ToscaYamlHelper.asMap(fieldBody), dataTypes);
        Assert.assertEquals(ToscaTypeDefinition.Kind.DATA_TYPE, type.getKind());
        Assert.assertEquals(dataTypes.get("NameValueMapping").getName(), type.getDataType().getName());
        Assert.assertEquals(dataTypes.get("NameValueMapping").getProperties(), type.getDataType().getProperties());
    }

    @Test
    public void parseTypeTestParseCollectionTypes() {
        Object fieldBody = ToscaYamlHelper.loadYaml("{ type: list, entry_schema: { type: NameValueMapping } }");
        Object dataTypeRaw =  ToscaYamlHelper.loadYaml("{data_types: {NameValueMapping: {properties: {name: {type: string, description: The name of the key-value pair., required: true}, value: {type: string, description: The value of the key-value pair., required: true}}}}}");
        Map<String, ToscaDataTypeDefinition> dataTypes = toscaNodeTypeParser.parseDataTypes(ToscaYamlHelper.asMap(dataTypeRaw));
        ToscaTypeDefinition type = toscaFieldParserSpy.parseType(ToscaYamlHelper.asMap(fieldBody), dataTypes);
        Assert.assertEquals(ToscaTypeDefinition.Kind.COLLECTION, type.getKind());
        Assert.assertEquals(ToscaCollectionType.LIST, type.getCollectionType());
        Assert.assertEquals(dataTypes.get("NameValueMapping").getName(), type.getEntrySchema().getDataType().getName());
        Assert.assertEquals(dataTypes.get("NameValueMapping").getProperties(), type.getEntrySchema().getDataType().getProperties());
    }

    @Test
    public void parseTypeTestReturnNullWhenTheTypeIsInvalid() {
        Object fieldBody = ToscaYamlHelper.loadYaml("{ type: invalid }");
        Assert.assertNull(toscaFieldParserSpy.parseType(ToscaYamlHelper.asMap(fieldBody), null));
    }

    @Test
    public void parseToscaBooleanFunctionTestParseValidValuesFunction() {
        Object validationBody = ToscaYamlHelper.loadYaml("$valid_values: [ $value, [TCP, UDP, ICMP, ALL] ]");
        ToscaFunction.ToscaBooleanFunction function = toscaFieldParserSpy.parseToscaBooleanFunction(ToscaYamlHelper.asMap(validationBody));
        ToscaBooleanFunctions.ValidValues validValuesFunction = (ToscaBooleanFunctions.ValidValues) function;
        Assert.assertEquals(List.of("TCP", "UDP", "ICMP", "ALL"), validValuesFunction.getValidValues());
    }
}
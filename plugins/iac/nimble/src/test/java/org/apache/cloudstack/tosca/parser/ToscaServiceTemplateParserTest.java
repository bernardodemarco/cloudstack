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
import org.apache.cloudstack.tosca.functions.ToscaBooleanFunctions;
import org.apache.cloudstack.tosca.model.ToscaInputDefinition;
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class ToscaServiceTemplateParserTest {
    private ToscaServiceTemplateParser toscaServiceTemplateParserSpy;

    @Mock
    private ToscaServiceTemplateParsingContext parsingContextMock;

    @Before
    public void setUp() {
        ToscaFieldParser toscaFieldParser = new ToscaFieldParser();
        toscaServiceTemplateParserSpy = Mockito.spy(new ToscaServiceTemplateParser(toscaFieldParser));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void parseServiceTemplateTestThrowExceptionWhenRequiredKeysOfTheRootSectionAreMissing() {
        String content = "{tosca_definitions_version: tosca_2_0, description: null}";
        toscaServiceTemplateParserSpy.parseServiceTemplate(content, null, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void parseServiceTemplateTestThrowExceptionWhenRequiredKeysOfTheServiceTemplateSectionAreMissing() {
        String content = "{tosca_definitions_version: tosca_2_0, description: null, service_template: {inputs: null}}";
        toscaServiceTemplateParserSpy.parseServiceTemplate(content, null, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void parseServiceTemplateTestThrowExceptionWhenUnknownKeysOfTheRootSectionArePresent() {
        String content = "{tosca_definitions_version: tosca_2_0, description: null, unknown-key: null, service_template: null}";
        toscaServiceTemplateParserSpy.parseServiceTemplate(content, null, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void parseServiceTemplateTestThrowExceptionWhenUnknownKeysOfTheServiceTemplateSectionArePresent() {
        String content = "{tosca_definitions_version: tosca_2_0, description: null, service_template: {inputs: null, node_templates: null, unknown-key: null}}";
        toscaServiceTemplateParserSpy.parseServiceTemplate(content, null, null);
    }

    @Test
    public void parseInputsTestAllInputsAreParsedSuccessfullyAndAddedToTheParsingContext() {
        String content = "{type: {type: string, validation: {$valid_values: [$value, [validvalue1, validvalue2]]}}, enabled: {description: Enabled input description, type: boolean, default_value: false}}";
        Map<String, ToscaInputDefinition> inputs = toscaServiceTemplateParserSpy.parseInputs(ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(content)), parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(0)).addError(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(parsingContextMock).setInputs(inputs);
        Assert.assertEquals(2, inputs.size());

        Assert.assertEquals("type", inputs.get("type").getName());
        Assert.assertNull(inputs.get("type").getDescription());
        Assert.assertNull(inputs.get("type").getDefaultValue());
        Assert.assertEquals(ToscaPrimitiveType.STRING, inputs.get("type").getType().getPrimitiveType());
        Assert.assertTrue(inputs.get("type").getValidation() instanceof ToscaBooleanFunctions.ValidValues);

        Assert.assertEquals("enabled", inputs.get("enabled").getName());
        Assert.assertEquals("Enabled input description", inputs.get("enabled").getDescription());
        Assert.assertFalse((Boolean) inputs.get("enabled").getDefaultValue());
        Assert.assertEquals(ToscaPrimitiveType.BOOLEAN, inputs.get("enabled").getType().getPrimitiveType());
        Assert.assertNull(inputs.get("enabled").getValidation());
    }

    @Test
    public void parseInputsTestAllCorrectInputDeclarationAreParsedAndErrorsOfUnknownKeysAndMissingInputTypeAreAddedToTheParsingContext() {
        String content = "{type: {unknownfield: string, validation: {$valid_values: [$value, [validvalue1, validvalue2]]}}, enabled: {description: Enabled input description, type: boolean, default_value: false}}";
        Map<String, ToscaInputDefinition> inputs = toscaServiceTemplateParserSpy.parseInputs(ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(content)), parsingContextMock);
        Mockito.verify(parsingContextMock).addError(Mockito.eq("Unknown key [unknownfield]."), Mockito.anyString());
        Mockito.verify(parsingContextMock).addError(Mockito.eq("The type of the input [type] was not specified or it is not supported."), Mockito.anyString());
        Assert.assertFalse(inputs.containsKey("type"));
        Assert.assertTrue(inputs.containsKey("enabled"));
    }

    @Test
    public void parseInputsTestAllCorrectInputDeclarationAreParsedAndErrorsOfDefaultValueTypeIncompatibilityAreAddedToTheParsingContext() {
        String content = "{type: {type: string, validation: {$valid_values: [$value, [validvalue1, validvalue2]]}}, enabled: {description: Enabled input description, type: boolean, default_value: nonbooleanvalue}}";
        Map<String, ToscaInputDefinition> inputs = toscaServiceTemplateParserSpy.parseInputs(ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(content)), parsingContextMock);

        String expectedErrorMessage = String.format("The provided default value [nonbooleanvalue] for the input [enabled] is not compatible with the input type [%s].", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.BOOLEAN));
        Mockito.verify(parsingContextMock).addError(Mockito.eq(expectedErrorMessage), Mockito.anyString());
        Assert.assertTrue(inputs.containsKey("type"));
        Assert.assertFalse(inputs.containsKey("enabled"));
    }

    @Test
    public void parseInputsTestAllCorrectInputDeclarationAreParsedAndErrorsOfInvalidValidationFunctionAreAddedToTheParsingContext() {
        String content = "{type: {type: string, validation: {$notvalidfunction: [$value, [validvalue1, validvalue2]]}}, enabled: {description: Enabled input description, type: boolean, default_value: false}}";
        Map<String, ToscaInputDefinition> inputs = toscaServiceTemplateParserSpy.parseInputs(ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(content)), parsingContextMock);

        Mockito.verify(parsingContextMock).addError(Mockito.eq("The validation function of the input [type] is not valid."), Mockito.anyString());
        Assert.assertFalse(inputs.containsKey("type"));
        Assert.assertTrue(inputs.containsKey("enabled"));
    }

    @Test
    public void validateRequiredToscaKeysTestAddErrorToTheParsingContextWhenRequiredKeysAreMissing() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{not-required-key: value}"));
        Set<String> requiredKeys = Set.of("required-key-1", "required-key-2");

        toscaServiceTemplateParserSpy.validateRequiredToscaKeys(content, requiredKeys, "Template Section", parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(2)).addError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void validateRequiredToscaKeysTestNotAddErrorToTheParsingContextWhenAllRequiredKeysArePresent() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{required-key-1: value-1, required-key-2: value-2}"));
        Set<String> requiredKeys = Set.of("required-key-1", "required-key-2");

        toscaServiceTemplateParserSpy.validateRequiredToscaKeys(content, requiredKeys, "Template Section", parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(0)).addError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void validateKnownToscaKeysTestAddErrorToTheParsingContextWhenAnUnknownKeyIsPresent() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{not-required-key: value}"));
        Set<String> knownKeys = Set.of("known-key-1", "known-key-2");

        toscaServiceTemplateParserSpy.validateKnownToscaKeys(content, knownKeys, "Template Section", parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(1)).addError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void validateKnownToscaKeysTestNotAddErrorToTheParsingContextWhenAllKeysAreKnown() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{known-key-2: value}"));
        Set<String> knownKeys = Set.of("known-key-1", "known-key-2");

        toscaServiceTemplateParserSpy.validateKnownToscaKeys(content, knownKeys, "Template Section", parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(0)).addError(Mockito.anyString(), Mockito.anyString());
    }
}
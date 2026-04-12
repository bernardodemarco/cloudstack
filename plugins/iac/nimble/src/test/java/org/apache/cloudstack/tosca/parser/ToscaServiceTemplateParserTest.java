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
import org.apache.cloudstack.fixtures.ToscaFixtures;
import org.apache.cloudstack.tosca.functions.ToscaBooleanFunctions;
import org.apache.cloudstack.tosca.model.ToscaInputDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
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
    public void checkMissingRequiredToscaKeysTestAddErrorsToTheParsingContextWhenThereAreMissingRequiredKeys() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{not-required-key: value}"));
        Set<String> requiredKeys = Set.of("required-key-1", "required-key-2");
        String templateSection = "Template Section";

        boolean missingRequiredKeys = toscaServiceTemplateParserSpy.checkMissingRequiredToscaKeys(content, requiredKeys, templateSection, parsingContextMock);
        Assert.assertTrue(missingRequiredKeys);
        Mockito.verify(parsingContextMock, Mockito.times(1)).addErrors(Mockito.anyList(), Mockito.eq(templateSection));
    }

    @Test
    public void checkMissingRequiredToscaKeysTestNotAddErrorsToTheParsingContextWhenThereAreNotMissingRequiredKeys() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{required-key-1: value-1, required-key-2: value-2}"));
        Set<String> requiredKeys = Set.of("required-key-1", "required-key-2");

        boolean missingRequiredKeys = toscaServiceTemplateParserSpy.checkMissingRequiredToscaKeys(content, requiredKeys, "Template Section", parsingContextMock);
        Assert.assertFalse(missingRequiredKeys);
        Mockito.verify(parsingContextMock, Mockito.times(0)).addErrors(Mockito.anyList(), Mockito.anyString());
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

    @Test
    public void parseNodeTemplatesTestHandleMissingRequiredToscaKeys() {
        String serviceTemplateYaml = "{instance: {properties: {type: SSVM, vcpus: 2, start-vm: false, max-usage: 10.572}}, pair: {}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Assert.assertEquals(0, nodeTemplates.size());
        Mockito.verify(parsingContextMock, Mockito.times(2)).addErrors(Mockito.anyList(), Mockito.anyString());
    }

    @Test
    public void parseNodeTemplatesTestHandleUnknownNodeType() {
        String serviceTemplateYaml = "{instance: {type: UnknownType, properties: {type: SSVM, vcpus: 2, start-vm: false, max-usage: 10.572}}, pair: {type: 10.575, properties: {name: Pair, public-key: {$get_input: public-key}}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Assert.assertEquals(0, nodeTemplates.size());
        Mockito.verify(parsingContextMock, Mockito.times(2)).addError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void parseNodeTemplatesTestHandleMissingRequiredKeys() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, start-vm: false, max-usage: 10.572}}, pair: {type: SshPair, properties: {name: Pair, public-key: {$get_input: public-key}}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());
        Mockito.when(parsingContextMock.getInputs()).thenReturn(ToscaFixtures.getToscaInputsForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Assert.assertEquals(1, nodeTemplates.size());
        Assert.assertTrue(nodeTemplates.containsKey("pair"));
        Mockito.verify(parsingContextMock, Mockito.times(1)).addErrors(Mockito.anyList(), Mockito.anyString());
    }

    @Test
    public void parseNodeTemplatesTestEnsureAllPropertyTypesAreParsedCorrectly() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, start-vm: false, max-usage: 10.572, ssh-key-pair-id: {$get_attribute: [pair, uuid]}, ssh-key-pair-name: {$get_property: [pair, name]}}}, pair: {type: SshPair, properties: {name: Pair, public-key: {$get_input: public-key}}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());
        Mockito.when(parsingContextMock.getInputs()).thenReturn(ToscaFixtures.getToscaInputsForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);

        Mockito.verify(parsingContextMock, Mockito.times(0)).addError(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(parsingContextMock, Mockito.times(0)).addErrors(Mockito.anyList(), Mockito.anyString());
        Assert.assertEquals(2, nodeTemplates.size());

        ToscaNodeTemplate instanceNodeTemplate = nodeTemplates.get("instance");
        Assert.assertEquals("SSVM", instanceNodeTemplate.getProperty("type").getEvaluatedValue());
        Assert.assertEquals(2, instanceNodeTemplate.getProperty("vcpus").getEvaluatedValue());
        Assert.assertEquals(false, instanceNodeTemplate.getProperty("start-vm").getEvaluatedValue());
        Assert.assertEquals(10.572, instanceNodeTemplate.getProperty("max-usage").getEvaluatedValue());

        Map<?, ?> keyPairidRawValue = (Map<?, ?>) instanceNodeTemplate.getProperty("ssh-key-pair-id").getRawValue();
        Assert.assertTrue(keyPairidRawValue.containsKey("$get_attribute"));
        Mockito.verify(parsingContextMock).addGetAttributeFunctionCalls(Mockito.eq(instanceNodeTemplate.getName()), Mockito.any());
        List<?> keyPairIdArgs = ToscaYamlHelper.asList(keyPairidRawValue.get("$get_attribute"));
        Assert.assertEquals("pair", keyPairIdArgs.get(0));
        Assert.assertEquals("uuid", keyPairIdArgs.get(1));

        Map<?, ?> keyPairNameRawValue = (Map<?, ?>) instanceNodeTemplate.getProperty("ssh-key-pair-name").getRawValue();
        Assert.assertTrue(keyPairNameRawValue.containsKey("$get_property"));
        Mockito.verify(parsingContextMock).addGetPropertyFunctionCalls(Mockito.eq(instanceNodeTemplate.getName()), Mockito.any());
        List<?> keyPairNameArgs = ToscaYamlHelper.asList(keyPairNameRawValue.get("$get_property"));
        Assert.assertEquals("pair", keyPairNameArgs.get(0));
        Assert.assertEquals("name", keyPairNameArgs.get(1));

        ToscaNodeTemplate pairNodeTemplate = nodeTemplates.get("pair");
        Assert.assertEquals("Pair", pairNodeTemplate.getProperty("name").getEvaluatedValue());
        Map<?, ?> publicKeyRawValue = (Map<?, ?>) pairNodeTemplate.getProperty("public-key").getRawValue();
        Assert.assertTrue(publicKeyRawValue.containsKey("$get_input"));
        Assert.assertEquals("public-key", publicKeyRawValue.get("$get_input"));
        Mockito.verify(parsingContextMock).addGetInputFunctionCalls(Mockito.eq(pairNodeTemplate.getName()), Mockito.any());
    }

    @Test
    public void parseNodeTemplatesTestHandleUnknownAndMissingRequiredProperties() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, start-vm: false, max-usage: 10.572}}, pair: {type: SshPair, properties: {unknown: unknown, name: Pair, public-key: pub-key}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Assert.assertEquals(1, nodeTemplates.size());
        Assert.assertTrue(nodeTemplates.containsKey("pair"));
        Mockito.verify(parsingContextMock).addError(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(parsingContextMock).addErrors(Mockito.anyList(), Mockito.anyString());
    }

    @Test
    public void parseNodeTemplatesTestHandleHandleIncorrectPrimitivePropertyValues() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: Invalid value, vcpus: false, start-vm: N, max-usage: Ten hours}}, pair: {type: SshPair, properties: {name: Pair, public-key: pub-key}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Assert.assertEquals(0, nodeTemplates.get("instance").getProperties().size());
        Mockito.verify(parsingContextMock, Mockito.times(4)).addError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void parseNodeTemplatesTestHandleHandleIncorrectGetInputPropertyValues() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, start-vm: false, max-usage: {$get_input: vm-type}, ssh-key-pair-id: {$get_attribute: [pair, uuid]}, ssh-key-pair-name: {$get_property: [pair, name]}}}, pair: {type: SshPair, properties: {name: Pair, public-key: {$get_input: unknown}}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());
        Mockito.when(parsingContextMock.getInputs()).thenReturn(ToscaFixtures.getToscaInputsForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Assert.assertEquals(5, nodeTemplates.get("instance").getProperties().size());
        Assert.assertEquals(1, nodeTemplates.get("pair").getProperties().size());
        Mockito.verify(parsingContextMock, Mockito.times(2)).addError(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(parsingContextMock, Mockito.never()).addGetInputFunctionCalls(Mockito.any(), Mockito.any());
    }

    @Test
    public void parseNodeTemplatesTestHandleHandleIncorrectGetAttributeAndGetPropertyValues() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: {$unknow-function: [value]}, vcpus: 2, start-vm: {$get_attribute}, max-usage: 10.572, ssh-key-pair-id: {$get_attribute: [pair, uuid, third-value]}, ssh-key-pair-name: {$get_property: [pair]}}}, pair: {type: SshPair, properties: {name: Pair, public-key: {$get_input: public-key}}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());
        Mockito.when(parsingContextMock.getInputs()).thenReturn(ToscaFixtures.getToscaInputsForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Assert.assertEquals(2, nodeTemplates.get("instance").getProperties().size());
        Assert.assertEquals(2, nodeTemplates.get("pair").getProperties().size());
        Mockito.verify(parsingContextMock, Mockito.times(4)).addError(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(parsingContextMock, Mockito.never()).addGetPropertyFunctionCalls(Mockito.any(), Mockito.any());
        Mockito.verify(parsingContextMock, Mockito.never()).addGetAttributeFunctionCalls(Mockito.any(), Mockito.any());
    }

    @Test
    public void parseNodeTemplatesTestEnsureCollectionTypesAreParsedCorrectly() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, offering-details: {memory: '1024', speed: '1000'}, ip-addresses: [10.0.0.2, 172.16.30.2]}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.never()).addError(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(parsingContextMock, Mockito.never()).addErrors(Mockito.anyList(), Mockito.anyString());

        Map<String, Object> details = ToscaYamlHelper.asMap(nodeTemplates.get("instance").getProperty("offering-details").getRawValue());
        Assert.assertEquals(2, details.size());
        Assert.assertTrue(details.containsKey("memory"));
        Assert.assertTrue(details.containsKey("speed"));

        List<?> ipAddresses = ToscaYamlHelper.asList(nodeTemplates.get("instance").getProperty("ip-addresses").getRawValue());
        Assert.assertEquals(2, ipAddresses.size());
        Assert.assertTrue(ipAddresses.contains("10.0.0.2"));
        Assert.assertTrue(ipAddresses.contains("172.16.30.2"));
    }

    @Test
    public void parseNodeTemplatesTestHandleIncorrectCollectionTypesValues() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, offering-details: [10.0.0.2, 172.16.30.2], ip-addresses: {memory: '1024', speed: '1000'}}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(2)).addError(Mockito.anyString(), Mockito.anyString());
        Assert.assertNull(nodeTemplates.get("instance").getProperty("offering-details"));
        Assert.assertNull(nodeTemplates.get("instance").getProperty("ip-addresses"));
    }

    @Test
    public void parseNodeTemplatesTestHandleIncorrectEntrySchemaValues() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, offering-details: {memory: 1024, speed: false}, ip-addresses: [{name: name, value: value}]}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(2)).addError(Mockito.anyString(), Mockito.anyString());
        Assert.assertNull(nodeTemplates.get("instance").getProperty("offering-details"));
        Assert.assertNull(nodeTemplates.get("instance").getProperty("ip-addresses"));
    }

    @Test
    public void parseNodeTemplatesTestEnsureDataTypesAreParsedCorrectly() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, name-value-mapping: [{name: name1, value: value1}, {name: name2}]}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(0)).addError(Mockito.anyString(), Mockito.anyString());
        List<?> nameValues = ToscaYamlHelper.asList(nodeTemplates.get("instance").getProperty("name-value-mapping").getRawValue());
        Map<String, Object> firstNameValue = ToscaYamlHelper.asMap(nameValues.get(0));
        Map<String, Object> secondNameValue = ToscaYamlHelper.asMap(nameValues.get(1));
        Assert.assertEquals(2, nameValues.size());
        Assert.assertEquals(2, firstNameValue.size());
        Assert.assertTrue(firstNameValue.containsKey("name"));
        Assert.assertTrue(firstNameValue.containsKey("value"));
        Assert.assertEquals(1, secondNameValue.size());
        Assert.assertTrue(secondNameValue.containsKey("name"));
    }

    @Test
    public void parseNodeTemplatesTestHandleUnknownKeyInDataType() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, name-value-mapping: [{name: name1, unknown-key: value1}]}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(1)).addError(Mockito.anyString(), Mockito.anyString());
        Assert.assertNull(nodeTemplates.get("instance").getProperty("name-value-mapping"));
    }

    @Test
    public void parseNodeTemplatesTestHandleMissingRequiredKeyInDataType() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, name-value-mapping: [{value: value2}]}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(1)).addError(Mockito.anyString(), Mockito.anyString());
        Assert.assertNull(nodeTemplates.get("instance").getProperty("name-value-mapping"));
    }

    @Test
    public void parseNodeTemplatesTestHandleMismatchingPropertyTypesInDataType() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, name-value-mapping: [{name: 100, value: false}]}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(1)).addError(Mockito.anyString(), Mockito.anyString());
        Assert.assertNull(nodeTemplates.get("instance").getProperty("name-value-mapping"));
    }

    @Test
    public void parseNodeTemplatesTestHandleMismatchingTypesInDataType() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, name-value-mapping: [[firstitem]]}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());

        Map<String, ToscaNodeTemplate> nodeTemplates = toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(1)).addError(Mockito.anyString(), Mockito.anyString());
        Assert.assertNull(nodeTemplates.get("instance").getProperty("name-value-mapping"));
    }

    @Test
    public void parseNodeTemplatesTestEnsureRequirementsDependenciesAreAddedToTheParsingContext() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, start-vm: false, max-usage: 10.572, ssh-key-pair-id: {$get_attribute: [pair, uuid]}, ssh-key-pair-name: {$get_property: [pair, name]}}, requirements: [{dependency: pair}, {dependency: other-pair}]}, pair: {type: SshPair, properties: {name: Pair, public-key: {$get_input: public-key}}}, other-pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());
        Mockito.when(parsingContextMock.getInputs()).thenReturn(ToscaFixtures.getToscaInputsForTests());

        toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.never()).addError(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(parsingContextMock).addNodeDependency(Mockito.eq("instance"), Mockito.eq("pair"));
        Mockito.verify(parsingContextMock).addNodeDependency(Mockito.eq("instance"), Mockito.eq("other-pair"));
    }

    @Test
    public void parseNodeTemplatesTestHandleIncorrectRequirementsDefinitions() {
        String serviceTemplateYaml = "{instance: {type: Vm, properties: {type: SSVM, vcpus: 2, start-vm: false, max-usage: 10.572, ssh-key-pair-id: {$get_attribute: [pair, uuid]}, ssh-key-pair-name: {$get_property: [pair, name]}}, requirements: [{}, {dependency: pair}, {dependency: other-pair}, {dependency: instance}, {dependency: 10.50}, {dependency: pair, invalid-key: value}, {invalid-key: pair}]}, pair: {type: SshPair, properties: {name: Pair, public-key: {$get_input: public-key}}}, other-pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}";
        Map<String, Object> serviceTemplate = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml(serviceTemplateYaml));
        Mockito.when(parsingContextMock.getProfile()).thenReturn(ToscaFixtures.getToscaProfileForTests());
        Mockito.when(parsingContextMock.getInputs()).thenReturn(ToscaFixtures.getToscaInputsForTests());
        Mockito.when(parsingContextMock.checkExistingDependency(Mockito.eq("instance"), Mockito.eq("pair"))).thenReturn(true);

        toscaServiceTemplateParserSpy.parseNodeTemplates(serviceTemplate, parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(6)).addError(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(parsingContextMock, Mockito.times(1)).addNodeDependency(Mockito.eq("instance"), Mockito.eq("other-pair"));
    }

    @Test
    public void parseServiceTemplateTestEnsureCorrectlyDefinedTemplatesAreCorrectlyRepresented() {
        String serviceTemplateYaml = "{service_template: {inputs: {vcpus: {type: integer}, public-key: {type: string}}, node_templates: {instance: {type: Vm, properties: {type: SSVM, vcpus: {$get_input: vcpus}, start-vm: false, max-usage: 10.572, ssh-key-pair-id: {$get_attribute: [pair, uuid]}, ssh-key-pair-name: {$get_property: [pair, name]}}, requirements: [{dependency: pair}, {dependency: other-pair}]}, pair: {type: SshPair, properties: {name: Pair, public-key: {$get_input: public-key}}}, other-pair: {type: SshPair, properties: {name: Other Pair, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaServiceTemplateParserSpy.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        Mockito.verify(parsingContextMock, Mockito.never()).addError(Mockito.anyString(), Mockito.anyString());
        Assert.assertEquals(3, serviceTemplate.getNodeTemplates().size());
        Assert.assertEquals(2, serviceTemplate.getInputs().size());
        Assert.assertEquals(2, serviceTemplate.getDependencyGraph().get("instance").size());
        Assert.assertNull(serviceTemplate.getDependencyGraph().get("pair"));
        Assert.assertNull(serviceTemplate.getDependencyGraph().get("other-pair"));
        Assert.assertEquals(2, serviceTemplate.getGetInputFunctionCalls().size());
    }

    @Test
    public void parseServiceTemplateTestEnsureRelationshipIsEstablishedOnlyViaGetPropertyAndGetAttribute() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, ssh-key-pair-name: {$get_property: [pair, name]}, vcpus: 2}}, pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaServiceTemplateParserSpy.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        Mockito.verify(parsingContextMock, Mockito.never()).addError(Mockito.anyString(), Mockito.anyString());
        Assert.assertEquals(2, serviceTemplate.getNodeTemplates().size());
        Assert.assertEquals(0, serviceTemplate.getInputs().size());
        Assert.assertEquals(1, serviceTemplate.getDependencyGraph().get("instance").size());
        Assert.assertNull(serviceTemplate.getDependencyGraph().get("pair"));
        Assert.assertEquals(0, serviceTemplate.getGetInputFunctionCalls().size());
    }

    @Test
    public void parseServiceTemplateTestHandleGetPropertyAndGetAttributesUsageErrors() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: {$get_property: [instance, type]}, ssh-key-pair-id: {$get_attribute: [instance, type]}, max-usage: {$get_property: [pair, unknown]}, start-vm: {$get_attribute: [pair, unknown]}, ssh-key-pair-name: {$get_property: [unknown, name]}, vcpus: {$get_attribute: [unknown, name]}}, requirements: [{dependency: pair}]}, pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}}}";
        InvalidParameterValueException exception = Assert.assertThrows(InvalidParameterValueException.class, () -> {
            toscaServiceTemplateParserSpy.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        });
        Assert.assertEquals(6, StringUtils.countMatches(exception.getMessage(), "ERROR"));
    }

    @Test
    public void parseServiceTemplateTestHandleRequirementsReferencingUnknownTarget() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, vcpus: 2}, requirements: [{dependency: unknown}]}}}}";
        InvalidParameterValueException exception = Assert.assertThrows(InvalidParameterValueException.class, () -> {
            toscaServiceTemplateParserSpy.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        });
        Assert.assertEquals(1, StringUtils.countMatches(exception.getMessage(), "ERROR"));
    }

    @Test
    public void parseServiceTemplateTestHandleUnmatchingTypesFromTheGetPropertyAndAttributeFunctions() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, vcpus: {$get_attribute: [pair, uuid]}, max-usage: {$get_property: [pair, name]}, start-vm: {$get_property: [pair, public-key]}}}, pair: {type: SshPair, properties: {name: {$get_property: [instance, max-usage]}, public-key: {$get_property: [instance, vcpus]}}}}}}";
        InvalidParameterValueException exception = Assert.assertThrows(InvalidParameterValueException.class, () -> {
            toscaServiceTemplateParserSpy.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        });
        Assert.assertEquals(5, StringUtils.countMatches(exception.getMessage(), "ERROR"));
    }

    @Test
    public void parseServiceTemplateTestHandleHandleCyclicGraphsDefinedByRequirements() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, vcpus: 2}, requirements: [{dependency: pair}]}, pair: {type: SshPair, properties: {name: Name, public-key: Key}, requirements: [{dependency: instance}]}}}}";
        InvalidParameterValueException exception = Assert.assertThrows(InvalidParameterValueException.class, () -> {
            toscaServiceTemplateParserSpy.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        });
        Assert.assertEquals(2, StringUtils.countMatches(exception.getMessage(), "ERROR"));
    }

    @Test
    public void parseServiceTemplateTestHandleHandleCyclicGraphsDefinedByGetPropertyAndGetAttributeFunctions() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, vcpus: 2, ssh-key-pair-id: {$get_attribute: [pair, uuid]}}}, pair: {type: SshPair, properties: {name: {$get_property: [instance, type]}, public-key: Key}}}}}";
        InvalidParameterValueException exception = Assert.assertThrows(InvalidParameterValueException.class, () -> {
            toscaServiceTemplateParserSpy.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        });
        Assert.assertEquals(2, StringUtils.countMatches(exception.getMessage(), "ERROR"));
    }

    @Test
    public void getApiParamsTestEnsureNodePropertiesAreCorrectlyConvertedToTheExpectedApiParamsFormat() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, ssh-key-pair-name: keypairname, vcpus: 2, ip-addresses: [10.0.0.1, 10.0.0.2], offering-details: {detail1: value1, detail2: value2}, name-value-mapping: [{name: name1, value: value1}, {name: name2, value: value2}], name-value-single-map: {name: namevaluesingmapname, value: namevaluesingmapvalue}}}, pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaServiceTemplateParserSpy.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);

        ToscaNodeTemplate vm = serviceTemplate.getNodeTemplates().get("instance");
        Map<String, String> vmPairApiParams = vm.getApiParams();
        Assert.assertEquals("VR", vmPairApiParams.get(vm.getProperty("type").getDefinition().getApiParameter()));
        Assert.assertEquals("keypairname", vmPairApiParams.get(vm.getProperty("ssh-key-pair-name").getDefinition().getApiParameter()));
        Assert.assertEquals("2", vmPairApiParams.get(vm.getProperty("vcpus").getDefinition().getApiParameter()));
        Assert.assertEquals("10.0.0.1,10.0.0.2", vmPairApiParams.get(vm.getProperty("ip-addresses").getDefinition().getApiParameter()));
        String offeringDetailsBaseName = vm.getProperty("offering-details").getDefinition().getApiParameter();
        Assert.assertEquals("value1", vmPairApiParams.get(offeringDetailsBaseName + "[0].detail1"));
        Assert.assertEquals("value2", vmPairApiParams.get(offeringDetailsBaseName + "[0].detail2"));
        String nameValueMappingBaseName = vm.getProperty("name-value-mapping").getDefinition().getApiParameter();
        Assert.assertEquals("name1", vmPairApiParams.get(nameValueMappingBaseName + "[0].name"));
        Assert.assertEquals("value1", vmPairApiParams.get(nameValueMappingBaseName + "[0].value"));
        Assert.assertEquals("name2", vmPairApiParams.get(nameValueMappingBaseName + "[1].name"));
        Assert.assertEquals("value2", vmPairApiParams.get(nameValueMappingBaseName + "[1].value"));
        String nameValueSingleMap = vm.getProperty("name-value-single-map").getDefinition().getApiParameter();
        Assert.assertEquals("namevaluesingmapname", vmPairApiParams.get(nameValueSingleMap + "[0].name"));
        Assert.assertEquals("namevaluesingmapvalue", vmPairApiParams.get(nameValueSingleMap + "[0].value"));

        ToscaNodeTemplate sshPair = serviceTemplate.getNodeTemplates().get("pair");
        Map<String, String> sshPairApiParams = sshPair.getApiParams();
        Assert.assertEquals("Pair", sshPairApiParams.get(sshPair.getProperty("name").getDefinition().getApiParameter()));
        Assert.assertEquals("Public Key", sshPairApiParams.get(sshPair.getProperty("public-key").getDefinition().getApiParameter()));
    }

}

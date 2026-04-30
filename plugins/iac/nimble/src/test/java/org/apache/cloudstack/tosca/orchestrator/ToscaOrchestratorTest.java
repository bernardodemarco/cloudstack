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
package org.apache.cloudstack.tosca.orchestrator;

import com.cloud.exception.InvalidParameterValueException;
import org.apache.cloudstack.fixtures.ToscaFixtures;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeVO;
import org.apache.cloudstack.tosca.model.ToscaInputDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;
import org.apache.cloudstack.tosca.parser.ToscaFieldParser;
import org.apache.cloudstack.tosca.parser.ToscaNodeTypeParser;
import org.apache.cloudstack.tosca.parser.ToscaParser;
import org.apache.cloudstack.tosca.parser.ToscaServiceTemplateParser;
import org.apache.cloudstack.tosca.parser.ToscaYamlHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ToscaOrchestratorTest {
    @Spy
    @InjectMocks
    private ToscaOrchestrator toscaOrchestratorSpy;

    @Mock
    private ToscaParser toscaParserMock;

    @Mock
    private ToscaServiceTemplate toscaServiceTemplateMock;

    @Mock
    private ToscaInputDefinition toscaInputDefinitionMock;

    private ToscaParser toscaParser;

    List<IacResourceTypeVO> iacResourceTypesMock = List.of(Mockito.mock(IacResourceTypeVO.class), Mockito.mock(IacResourceTypeVO.class));

    @Before
    public void setUp() {
        ToscaFieldParser toscaFieldParser = new ToscaFieldParser();
        ToscaNodeTypeParser toscaNodeTypeParser = new ToscaNodeTypeParser(toscaFieldParser);
        ToscaServiceTemplateParser toscaServiceTemplateParser = new ToscaServiceTemplateParser(toscaFieldParser);
        toscaParser = new ToscaParser(toscaNodeTypeParser, toscaServiceTemplateParser);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void resolveServiceTemplateInputsTestThrowExceptionWhenUnknowInputIsProvided() {
        Mockito.when(toscaServiceTemplateMock.getInputs()).thenReturn(Map.of("input", toscaInputDefinitionMock));
        toscaOrchestratorSpy.resolveServiceTemplateInputs(toscaServiceTemplateMock, Map.of("unknown-input", "Input Value"));
    }

    @Test
    public void resolveServiceTemplateInputsTestNotResolveInputsWhenTheServiceTemplateHasNoInputs() {
        toscaOrchestratorSpy.resolveServiceTemplateInputs(toscaServiceTemplateMock, Map.of());
        Mockito.verify(toscaServiceTemplateMock, Mockito.never()).getUnresolvedPropertiesByGetInput();
    }

    @Test
    public void resolveServiceTemplateInputsTestCorrectlyResolveAllPrimitiveTypes() {
        String serviceTemplateYaml = "{service_template: {inputs: {vcpus: {type: integer}, max-usage-input: {type: float}, public-key: {type: string}, start-vm-input: {type: boolean}}, node_templates: {instance: {type: Vm, properties: {type: SSVM, vcpus: {$get_input: vcpus}, max-usage: {$get_input: max-usage-input}, start-vm: {$get_input: start-vm-input}, ssh-key-pair-id: {$get_attribute: [pair, uuid]}, ssh-key-pair-name: {$get_property: [pair, name]}}, requirements: [{dependency: pair}, {dependency: other-pair}]}, pair: {type: SshPair, properties: {name: {$get_input: public-key}, public-key: {$get_input: public-key}}}, other-pair: {type: SshPair, properties: {name: Other Pair, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);

        toscaOrchestratorSpy.resolveServiceTemplateInputs(serviceTemplate, Map.of("vcpus", "2", "max-usage-input", "2.11255", "public-key", "Public Key", "start-vm-input", "true"));
        Assert.assertEquals(2, serviceTemplate.getNodeTemplates().get("instance").getProperty("vcpus").getEvaluatedValue());
        Assert.assertEquals(2.11255, serviceTemplate.getNodeTemplates().get("instance").getProperty("max-usage").getEvaluatedValue());
        Assert.assertEquals("Public Key", serviceTemplate.getNodeTemplates().get("pair").getProperty("public-key").getEvaluatedValue());
        Assert.assertEquals("Public Key", serviceTemplate.getNodeTemplates().get("pair").getProperty("name").getEvaluatedValue());
        Assert.assertEquals(true, serviceTemplate.getNodeTemplates().get("instance").getProperty("start-vm").getEvaluatedValue());
    }

    @Test(expected = InvalidParameterValueException.class)
    public void resolveServiceTemplateInputsTestThrowExceptionWhenPropertyValidationFunctionDoesNotSucceeds() {
        String serviceTemplateYaml = "{service_template: {inputs: {vm-type: {type: string}}, node_templates: {instance: {type: Vm, properties: {type: {$get_input: vm-type}, vcpus: 2}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);

        toscaOrchestratorSpy.resolveServiceTemplateInputs(serviceTemplate, Map.of("vm-type", "UserVM"));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void resolveServiceTemplateInputsTestThrowExceptionWhenInputValidationFunctionDoesNotSucceeds() {
        String serviceTemplateYaml = "{service_template: {inputs: {key-pair-name: {type: string, validation: { $valid_values: [ $value, [Pair1, Pair2] ] }}}, node_templates: {instance: {type: Vm, properties: {type: VR, vcpus: 2, ssh-key-pair-name: {$get_input: key-pair-name}}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);

        toscaOrchestratorSpy.resolveServiceTemplateInputs(serviceTemplate, Map.of("key-pair-name", "Pair"));
    }

    @Test
    public void resolveServiceTemplateInputsTestCorrectlyResolveInputsWhenAllValidationFunctionsSucceed() {
        String serviceTemplateYaml = "{service_template: {inputs: {vm-type: {type: string, validation: { $valid_values: [ $value, [CPVM, SSVM] ]}}}, node_templates: {instance: {type: Vm, properties: {type: {$get_input: vm-type}, vcpus: 2}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);

        toscaOrchestratorSpy.resolveServiceTemplateInputs(serviceTemplate, Map.of("vm-type", "CPVM"));
        Assert.assertEquals("CPVM", serviceTemplate.getNodeTemplates().get("instance").getProperty("type").getEvaluatedValue());
    }

    @Test
    public void resolveServiceTemplateInputsTestConsiderInputDefaultValueWhenNotProvided() {
        String serviceTemplateYaml = "{service_template: {inputs: {vm-type: {type: string, validation: { $valid_values: [ $value, [CPVM, SSVM] ]}, default_value: CPVM}}, node_templates: {instance: {type: Vm, properties: {type: {$get_input: vm-type}, vcpus: 2}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);

        toscaOrchestratorSpy.resolveServiceTemplateInputs(serviceTemplate, Map.of());
        Assert.assertEquals("CPVM", serviceTemplate.getNodeTemplates().get("instance").getProperty("type").getEvaluatedValue());
    }

    @Test(expected = InvalidParameterValueException.class)
    public void resolveServiceTemplateInputsTestThrowExceptionWhenInputIsNotDefinedAndItDoesNotHaveADefaultValue() {
        String serviceTemplateYaml = "{service_template: {inputs: {vm-type: {type: string, validation: { $valid_values: [ $value, [CPVM, SSVM] ]}}}, node_templates: {instance: {type: Vm, properties: {type: {$get_input: vm-type}, vcpus: 2}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);

        toscaOrchestratorSpy.resolveServiceTemplateInputs(serviceTemplate, Map.of());
    }

    @Test
    public void resolveServiceTemplateInputsTestCorrectlyResolveAllGetInputFunctionCallsInsideOfCollections() {
        String serviceTemplateYaml = "{service_template: {inputs: {first-ip: {type: string}, second-ip: {type: string}, first-detail: {type: string}, second-detail: {type: string}}, node_templates: {instance: {type: Vm, properties: {type: SSVM, vcpus: 2, ip-addresses: [{$get_input: first-ip}, {$get_input: second-ip}], offering-details: {detail1: {$get_input: first-detail}, detail2: {$get_input: second-detail}}, name-value-mapping: [{name: {$get_input: first-detail}, value: {$get_input: first-ip}}, {name: {$get_input: second-detail}, value: {$get_input: second-ip}}]}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);

        toscaOrchestratorSpy.resolveServiceTemplateInputs(serviceTemplate, Map.of("first-ip", "10.0.0.1", "second-ip", "10.0.0.2", "first-detail", "1st detail", "second-detail", "2nd detail"));

        List<?> instanceIpAddresses = ToscaYamlHelper.asList(serviceTemplate.getNodeTemplates().get("instance").getProperty("ip-addresses").getEvaluatedValue());
        Assert.assertEquals(2, instanceIpAddresses.size());
        Assert.assertEquals("10.0.0.1", instanceIpAddresses.get(0));
        Assert.assertEquals("10.0.0.2", instanceIpAddresses.get(1));

        Map<String, Object> offeringDetails = ToscaYamlHelper.asMap(serviceTemplate.getNodeTemplates().get("instance").getProperty("offering-details").getEvaluatedValue());
        Assert.assertEquals(2, offeringDetails.size());
        Assert.assertEquals("1st detail", offeringDetails.get("detail1"));
        Assert.assertEquals("2nd detail", offeringDetails.get("detail2"));

        List<?> nameValueMappings = ToscaYamlHelper.asList(serviceTemplate.getNodeTemplates().get("instance").getProperty("name-value-mapping").getEvaluatedValue());
        Assert.assertEquals(2, nameValueMappings.size());
        Assert.assertEquals("1st detail", ToscaYamlHelper.asMap(nameValueMappings.get(0)).get("name"));
        Assert.assertEquals("10.0.0.1", ToscaYamlHelper.asMap(nameValueMappings.get(0)).get("value"));
        Assert.assertEquals("2nd detail", ToscaYamlHelper.asMap(nameValueMappings.get(1)).get("name"));
        Assert.assertEquals("10.0.0.2", ToscaYamlHelper.asMap(nameValueMappings.get(1)).get("value"));
    }

    @Test
    public void populateNodeTemplateAttributesTestSuccessfullyPopulateNodeAttributes() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, ssh-key-pair-name: {$get_property: [pair, name]}, vcpus: 2, ip-addresses: [10.0.0.1, 10.0.0.2]}}, other-instance: {type: Vm, properties: {type: SSVM, ssh-key-pair-name: {$get_attribute: [pair, uuid]}, vcpus: 1, ip-addresses: [10.0.0.1]}}, pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        ToscaNodeTemplate instanceNodeTemplate = serviceTemplate.getNodeTemplates().get("instance");
        Map<String, Object> potentialAttributes = Map.of("uuid", "UUID Value", "name", "VM Name", "vCPUs", 2, "systemVm", false);
        toscaOrchestratorSpy.populateNodeTemplateAttributes(instanceNodeTemplate, potentialAttributes);
        Map<String, Object> nodeTemplateAttributes = instanceNodeTemplate.getAttributes();
        Assert.assertEquals(instanceNodeTemplate.getType().getAttributes().size(), nodeTemplateAttributes.size());
        Assert.assertEquals(potentialAttributes.get("uuid"), nodeTemplateAttributes.get("uuid"));
    }

    @Test
    public void populateNodeTemplateAttributesTestNotPopulateAttributeWhenItsValueIsNotAvailable() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, ssh-key-pair-name: {$get_property: [pair, name]}, vcpus: 2, ip-addresses: [10.0.0.1, 10.0.0.2]}}, other-instance: {type: Vm, properties: {type: SSVM, ssh-key-pair-name: {$get_attribute: [pair, uuid]}, vcpus: 1, ip-addresses: [10.0.0.1]}}, pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        ToscaNodeTemplate instanceNodeTemplate = serviceTemplate.getNodeTemplates().get("instance");
        Map<String, Object> potentialAttributes = Map.of("name", "VM Name", "vCPUs", 2, "systemVm", false);
        toscaOrchestratorSpy.populateNodeTemplateAttributes(instanceNodeTemplate, potentialAttributes);
        Map<String, Object> nodeTemplateAttributes = instanceNodeTemplate.getAttributes();
        Assert.assertEquals(0, nodeTemplateAttributes.size());
        Assert.assertFalse(nodeTemplateAttributes.containsKey("uuid"));
    }

    @Test
    public void executeGetAttributeAndGetPropertyFunctionCallsTestSuccessfullyExecuteFunctionCalls() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, ssh-key-pair-name: {$get_property: [pair, name]}, vcpus: 2, ip-addresses: [10.0.0.1, 10.0.0.2]}}, other-instance: {type: Vm, properties: {type: SSVM, ssh-key-pair-name: {$get_attribute: [pair, uuid]}, vcpus: 1, ip-addresses: [10.0.0.1]}}, pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        serviceTemplate.getNodeTemplates().get("pair").addAttribute("uuid", "VR");

        toscaOrchestratorSpy.executeGetAttributeAndGetPropertyFunctionCalls(serviceTemplate.getNodeTemplates().get("instance"), serviceTemplate);
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("pair").getProperty("name").getEvaluatedValue(), serviceTemplate.getNodeTemplates().get("instance").getProperty("ssh-key-pair-name").getEvaluatedValue());

        toscaOrchestratorSpy.executeGetAttributeAndGetPropertyFunctionCalls(serviceTemplate.getNodeTemplates().get("other-instance"), serviceTemplate);
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("pair").getAttribute("uuid"), serviceTemplate.getNodeTemplates().get("other-instance").getProperty("ssh-key-pair-name").getEvaluatedValue());
    }

    @Test
    public void executeGetAttributeAndGetPropertyFunctionCallsTestSuccessfullyExecuteFunctionCallsContainedInsideOfCollections() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, vcpus: 2, name-value-mapping: [{name: {$get_property: [pair, name]}, value: {$get_attribute: [pair, uuid]}}, {name: {$get_property: [other-instance, type]}, value: {$get_attribute: [other-instance, uuid]}}], ip-addresses: [{$get_property: [other-instance, type]}, {$get_attribute: [other-instance, uuid]}]}}, other-instance: {type: Vm, properties: {type: {$get_property: [pair, name]}, vcpus: 1, offering-details: {detail1: {$get_attribute: [pair, uuid]}, detail2: {$get_property: [pair, public-key]}}}}, pair: {type: SshPair, properties: {name: SSVM, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        serviceTemplate.getNodeTemplates().get("other-instance").addAttribute("uuid", "otherinstanceuuid");
        serviceTemplate.getNodeTemplates().get("pair").addAttribute("uuid", "pairuuid");

        toscaOrchestratorSpy.executeGetAttributeAndGetPropertyFunctionCalls(serviceTemplate.getNodeTemplates().get("other-instance"), serviceTemplate);
        Map<String, Object> offeringDetails = ToscaYamlHelper.asMap(serviceTemplate.getNodeTemplates().get("other-instance").getProperty("offering-details").getEvaluatedValue());
        Assert.assertEquals(2, offeringDetails.size());
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("pair").getAttribute("uuid"), offeringDetails.get("detail1"));
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("pair").getProperty("public-key").getEvaluatedValue(), offeringDetails.get("detail2"));

        toscaOrchestratorSpy.executeGetAttributeAndGetPropertyFunctionCalls(serviceTemplate.getNodeTemplates().get("instance"), serviceTemplate);
        List<?> nameValueMappings = ToscaYamlHelper.asList(serviceTemplate.getNodeTemplates().get("instance").getProperty("name-value-mapping").getEvaluatedValue());
        Assert.assertEquals(2, nameValueMappings.size());
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("pair").getProperty("name").getEvaluatedValue(), ToscaYamlHelper.asMap(nameValueMappings.get(0)).get("name"));
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("pair").getAttribute("uuid"), ToscaYamlHelper.asMap(nameValueMappings.get(0)).get("value"));
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("other-instance").getProperty("type").getEvaluatedValue(), ToscaYamlHelper.asMap(nameValueMappings.get(1)).get("name"));
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("other-instance").getAttribute("uuid"), ToscaYamlHelper.asMap(nameValueMappings.get(1)).get("value"));

        List<?> instanceIpAddresses = ToscaYamlHelper.asList(serviceTemplate.getNodeTemplates().get("instance").getProperty("ip-addresses").getEvaluatedValue());
        Assert.assertEquals(2, offeringDetails.size());
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("other-instance").getProperty("type").getEvaluatedValue(), instanceIpAddresses.get(0));
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("other-instance").getAttribute("uuid"), instanceIpAddresses.get(1));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void executeGetAttributeAndGetPropertyFunctionCallsThrowExceptionWhenTargetAttributeIsNotAvailable() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: {$get_attribute: [pair, uuid]}, ssh-key-pair-name: Name, vcpus: 2, ip-addresses: [10.0.0.1, 10.0.0.2]}}, pair: {type: SshPair, properties: {name: SSVM, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        toscaOrchestratorSpy.executeGetAttributeAndGetPropertyFunctionCalls(serviceTemplate.getNodeTemplates().get("instance"), serviceTemplate);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void executeGetAttributeAndGetPropertyFunctionCallsTestFailsAfterResolvingAPropertyAndGetPropertyFunctionCalls() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: {$get_property: [pair, name]}, ssh-key-pair-name: Name, vcpus: 2, ip-addresses: [10.0.0.1, 10.0.0.2]}}, pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        toscaOrchestratorSpy.executeGetAttributeAndGetPropertyFunctionCalls(serviceTemplate.getNodeTemplates().get("instance"), serviceTemplate);
    }

    @Test
    public void executeGetAttributeAndGetPropertyFunctionCallsTestResolvePropertyWhenValidationFunctionSucceeds() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: {$get_property: [pair, name]}, ssh-key-pair-name: Name, vcpus: 2, ip-addresses: [10.0.0.1, 10.0.0.2]}}, pair: {type: SshPair, properties: {name: SSVM, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        toscaOrchestratorSpy.executeGetAttributeAndGetPropertyFunctionCalls(serviceTemplate.getNodeTemplates().get("instance"), serviceTemplate);
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("pair").getProperty("name").getEvaluatedValue(), serviceTemplate.getNodeTemplates().get("instance").getProperty("type").getEvaluatedValue());
    }

    @Test
    public void loadToscaProfileTestEachIacResourceTypeShouldBeParsed() {
        String firstResourceTypeContent = "{description: First resource type content}";
        String secondResourceTypeContent = "{description: Second resource type content}";
        Mockito.when(iacResourceTypesMock.get(0).getContent()).thenReturn(firstResourceTypeContent);
        Mockito.when(iacResourceTypesMock.get(1).getContent()).thenReturn(secondResourceTypeContent);

        List<ToscaNodeType> toscaNodeTypesMock = List.of(Mockito.mock(ToscaNodeType.class), Mockito.mock(ToscaNodeType.class));
        Mockito.when(toscaNodeTypesMock.get(0).getName()).thenReturn("First TOSCA node type");
        Mockito.when(toscaNodeTypesMock.get(1).getName()).thenReturn("Second TOSCA node type");

        Mockito.when(toscaParserMock.parseNodeTypeDefinitionFile(Mockito.eq(firstResourceTypeContent))).thenReturn(toscaNodeTypesMock.get(0));
        Mockito.when(toscaParserMock.parseNodeTypeDefinitionFile(Mockito.eq(secondResourceTypeContent))).thenReturn(toscaNodeTypesMock.get(1));

        toscaOrchestratorSpy.loadToscaProfile(iacResourceTypesMock);
        Map<String, ToscaNodeType> profile = toscaOrchestratorSpy.getToscaProfile();
        Assert.assertEquals(iacResourceTypesMock.size(), profile.size());
        Assert.assertTrue(profile.containsKey(toscaNodeTypesMock.get(0).getName()));
        Assert.assertTrue(profile.containsKey(toscaNodeTypesMock.get(1).getName()));
    }
}

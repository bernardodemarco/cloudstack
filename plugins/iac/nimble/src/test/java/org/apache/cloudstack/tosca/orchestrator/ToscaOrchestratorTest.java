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

import org.apache.cloudstack.fixtures.ToscaFixtures;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeVO;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;
import org.apache.cloudstack.tosca.parser.ToscaFieldParser;
import org.apache.cloudstack.tosca.parser.ToscaNodeTypeParser;
import org.apache.cloudstack.tosca.parser.ToscaParser;
import org.apache.cloudstack.tosca.parser.ToscaServiceTemplateParser;
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

    private ToscaParser toscaParser;

    List<IacResourceTypeVO> iacResourceTypesMock = List.of(Mockito.mock(IacResourceTypeVO.class), Mockito.mock(IacResourceTypeVO.class));

    @Before
    public void setUp() {
        ToscaFieldParser toscaFieldParser = new ToscaFieldParser();
        ToscaNodeTypeParser toscaNodeTypeParser = new ToscaNodeTypeParser(toscaFieldParser);
        ToscaServiceTemplateParser toscaServiceTemplateParser = new ToscaServiceTemplateParser(toscaFieldParser);
        toscaParser = new ToscaParser(toscaNodeTypeParser, toscaServiceTemplateParser);
    }

    @Test
    public void resolveUnresolvedPropertiesByToscaFunctionTest() {
        String serviceTemplateYaml = "{service_template: {node_templates: {instance: {type: Vm, properties: {type: VR, ssh-key-pair-name: {$get_property: [pair, name]}, vcpus: 2, ip-addresses: [10.0.0.1, 10.0.0.2]}}, other-instance: {type: Vm, properties: {type: SSVM, ssh-key-pair-name: {$get_attribute: [pair, uuid]}, vcpus: 1, ip-addresses: [10.0.0.1]}}, pair: {type: SshPair, properties: {name: Pair, public-key: Public Key}}}}}";
        ToscaServiceTemplate serviceTemplate = toscaParser.parseServiceTemplate(serviceTemplateYaml, ToscaFixtures.getToscaProfileForTests(), null);
        serviceTemplate.getNodeTemplates().get("pair").addAttribute("uuid", "UUID");

        toscaOrchestratorSpy.resolveUnresolvedPropertiesByToscaFunction(serviceTemplate.getNodeTemplates().get("instance"), serviceTemplate, "$get_property");
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("pair").getProperty("name").getEvaluatedValue(), serviceTemplate.getNodeTemplates().get("instance").getProperty("ssh-key-pair-name").getEvaluatedValue());

        toscaOrchestratorSpy.resolveUnresolvedPropertiesByToscaFunction(serviceTemplate.getNodeTemplates().get("other-instance"), serviceTemplate, "$get_attribute");
        Assert.assertEquals(serviceTemplate.getNodeTemplates().get("pair").getAttribute("uuid"), serviceTemplate.getNodeTemplates().get("other-instance").getProperty("ssh-key-pair-name").getEvaluatedValue());
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

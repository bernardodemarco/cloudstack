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

import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeVO;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.parser.ToscaParser;
import org.junit.Assert;
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

    List<IacResourceTypeVO> iacResourceTypesMock = List.of(Mockito.mock(IacResourceTypeVO.class), Mockito.mock(IacResourceTypeVO.class));

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

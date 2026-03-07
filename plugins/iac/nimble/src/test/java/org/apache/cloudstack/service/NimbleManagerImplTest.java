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
package org.apache.cloudstack.service;

import com.cloud.utils.Pair;
import org.apache.cloudstack.api.command.ListIacResourceTypesCmd;
import org.apache.cloudstack.api.response.IacResourceTypeResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NimbleResponseBuilder;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeDao;
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
public class NimbleManagerImplTest {
    @Spy
    @InjectMocks
    private NimbleManagerImpl nimbleServiceSpy;

    @Mock
    private ListIacResourceTypesCmd listIacResourceTypesCmdMock;

    @Mock
    private IacResourceTypeDao iacResourceTypeDaoMock;

    @Mock
    private NimbleResponseBuilder nimbleResponseBuilderMock;

    @Mock
    private ToscaParser toscaParserMock;

    @Test
    public void listIacResourceTypesTestShouldGenerateAResponseForEachIacResourceType() {
        long iacResourceTypeId = 1L;
        String iacResourceTypeName = "Iac Resource Type Name";
        String iacResourceTypeKeyword = "Iac Resource Type Keyword";
        long pageSize = 5L;
        long startIndex = 1L;

        Mockito.when(listIacResourceTypesCmdMock.getId()).thenReturn(iacResourceTypeId);
        Mockito.when(listIacResourceTypesCmdMock.getName()).thenReturn(iacResourceTypeName);
        Mockito.when(listIacResourceTypesCmdMock.getKeyword()).thenReturn(iacResourceTypeKeyword);
        Mockito.when(listIacResourceTypesCmdMock.getPageSizeVal()).thenReturn(pageSize);
        Mockito.when(listIacResourceTypesCmdMock.getStartIndex()).thenReturn(startIndex);

        List<IacResourceTypeVO> iacResourceTypesMock = List.of(Mockito.mock(IacResourceTypeVO.class), Mockito.mock(IacResourceTypeVO.class));
        Mockito.when(iacResourceTypeDaoMock.listIacResourceTypes(Mockito.eq(iacResourceTypeId), Mockito.eq(iacResourceTypeName),
                Mockito.eq(iacResourceTypeKeyword), Mockito.eq(pageSize), Mockito.eq(startIndex)))
                .thenReturn(new Pair<>(iacResourceTypesMock, iacResourceTypesMock.size()));

        List<IacResourceTypeResponse> iacResourceTypeResponsesMock = List.of(Mockito.mock(IacResourceTypeResponse.class), Mockito.mock(IacResourceTypeResponse.class));
        for (int i = 0; i < iacResourceTypesMock.size(); i++) {
            Mockito.when(nimbleResponseBuilderMock.createIacResourceTypeResponse(Mockito.eq(iacResourceTypesMock.get(i))))
                    .thenReturn(iacResourceTypeResponsesMock.get(i));
        }

        ListResponse<IacResourceTypeResponse> response = nimbleServiceSpy.listIacResourceTypes(listIacResourceTypesCmdMock);
        for (IacResourceTypeVO iacResourceType : iacResourceTypesMock) {
            Mockito.verify(nimbleResponseBuilderMock).createIacResourceTypeResponse(Mockito.eq(iacResourceType));
        }
        Assert.assertEquals(iacResourceTypeResponsesMock, response.getResponses());
        Assert.assertEquals(iacResourceTypeResponsesMock.size(), response.getCount().intValue());
    }

    @Test
    public void loadToscaProfileTestEachIacResourceTypeShouldBeParsed() {
        String firstIacResourceTypeName = "First Iac Resource Type Name";
        String secondIacResourceTypeName = "Second Iac Resource Type Name";

        List<IacResourceTypeVO> iacResourceTypesMock = List.of(Mockito.mock(IacResourceTypeVO.class), Mockito.mock(IacResourceTypeVO.class));
        Mockito.when(iacResourceTypesMock.get(0).getName()).thenReturn(firstIacResourceTypeName);
        Mockito.when(iacResourceTypesMock.get(1).getName()).thenReturn(secondIacResourceTypeName);

        Mockito.when(iacResourceTypeDaoMock.listAll()).thenReturn(iacResourceTypesMock);

        List<ToscaNodeType> toscaNodeTypesMock = List.of(Mockito.mock(ToscaNodeType.class), Mockito.mock(ToscaNodeType.class));
        Mockito.when(toscaNodeTypesMock.get(0).getName()).thenReturn("First TOSCA node type");
        Mockito.when(toscaNodeTypesMock.get(1).getName()).thenReturn("Second TOSCA node type");

        Mockito.when(toscaParserMock.parseNodeType(Mockito.eq(firstIacResourceTypeName), Mockito.any())).thenReturn(toscaNodeTypesMock.get(0));
        Mockito.when(toscaParserMock.parseNodeType(Mockito.eq(secondIacResourceTypeName), Mockito.any())).thenReturn(toscaNodeTypesMock.get(1));

        Map<String, ToscaNodeType> profile = nimbleServiceSpy.loadToscaProfile();
        Mockito.verify(toscaParserMock, Mockito.times(iacResourceTypesMock.size())).parseNodeType(Mockito.any(), Mockito.any());
        Assert.assertEquals(iacResourceTypesMock.size(), profile.size());
        for (ToscaNodeType toscaNodeType : toscaNodeTypesMock) {
            Assert.assertTrue(profile.containsKey(toscaNodeType.getName()));
        }
    }
}

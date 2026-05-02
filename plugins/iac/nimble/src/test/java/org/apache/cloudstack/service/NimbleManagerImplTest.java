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
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.discovery.ApiDiscoveryService;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceType;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeDao;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeVO;
import org.apache.cloudstack.tosca.orchestrator.ToscaOrchestrator;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

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
    private ToscaOrchestrator toscaOrchestratorMock;

    @Mock
    private ApiDiscoveryService apiDiscoveryServiceMock;

    @Mock
    private CallContext callContextMock;

    List<IacResourceTypeVO> iacResourceTypesMock = List.of(Mockito.mock(IacResourceTypeVO.class), Mockito.mock(IacResourceTypeVO.class));

    @Test
    public void listIacResourceTypesTestShouldGenerateAResponseForEachIacResourceTypeNotIncludingTheirContentsByDefault() {
        long iacResourceTypeId = 1L;
        String iacResourceTypeName = "Iac Resource Type Name";
        IacResourceType.Category iacResourceTypeCategory = IacResourceType.Category.CLOUD_ACCESS_MANAGEMENT;
        String iacResourceTypeKeyword = "Iac Resource Type Keyword";
        long pageSize = 5L;
        long startIndex = 1L;

        try (MockedStatic<CallContext> callContextStaticMock = Mockito.mockStatic(CallContext.class)) {
            callContextStaticMock.when(CallContext::current).thenReturn(callContextMock);
            Mockito.when(listIacResourceTypesCmdMock.getId()).thenReturn(iacResourceTypeId);
            Mockito.when(listIacResourceTypesCmdMock.getName()).thenReturn(iacResourceTypeName);
            Mockito.when(listIacResourceTypesCmdMock.getCategory()).thenReturn(iacResourceTypeCategory);
            Mockito.when(listIacResourceTypesCmdMock.getKeyword()).thenReturn(iacResourceTypeKeyword);
            Mockito.when(listIacResourceTypesCmdMock.getPageSizeVal()).thenReturn(pageSize);
            Mockito.when(listIacResourceTypesCmdMock.getStartIndex()).thenReturn(startIndex);
            Mockito.when(iacResourceTypeDaoMock.listIacResourceTypes(Mockito.eq(iacResourceTypeId), Mockito.eq(iacResourceTypeName),
                            Mockito.eq(iacResourceTypeCategory), Mockito.eq(iacResourceTypeKeyword), Mockito.eq(pageSize), Mockito.eq(startIndex)))
                    .thenReturn(iacResourceTypesMock);

            Mockito.when(toscaOrchestratorMock.getNodeTypeApis(Mockito.any())).thenReturn(new Pair<>("provisioningApi", "rollbackApi"));
            Mockito.when(apiDiscoveryServiceMock.listApis(Mockito.any(), Mockito.anyString())).thenReturn(new ListResponse<>());

            List<IacResourceTypeResponse> iacResourceTypeResponsesMock = List.of(Mockito.mock(IacResourceTypeResponse.class), Mockito.mock(IacResourceTypeResponse.class));
            Mockito.when(nimbleResponseBuilderMock.createIacResourceTypeResponse(Mockito.eq(iacResourceTypesMock.get(0)), Mockito.eq(false))).thenReturn(iacResourceTypeResponsesMock.get(0));
            Mockito.when(nimbleResponseBuilderMock.createIacResourceTypeResponse(Mockito.eq(iacResourceTypesMock.get(1)), Mockito.eq(false))).thenReturn(iacResourceTypeResponsesMock.get(1));

            ListResponse<IacResourceTypeResponse> response = nimbleServiceSpy.listIacResourceTypes(listIacResourceTypesCmdMock);
            Assert.assertEquals(iacResourceTypeResponsesMock, response.getResponses());
            Assert.assertEquals(iacResourceTypeResponsesMock.size(), response.getCount().intValue());
        }
    }

    @Test
    public void listIacResourceTypesTestShouldGenerateAResponseForEachIacResourceTypeIncludingTheirContentsWhenAsked() {
        long iacResourceTypeId = 1L;
        String iacResourceTypeName = "Iac Resource Type Name";
        IacResourceType.Category iacResourceTypeCategory = IacResourceType.Category.CLOUD_ACCESS_MANAGEMENT;
        String iacResourceTypeKeyword = "Iac Resource Type Keyword";
        boolean showIacResourceTypeContent = true;
        long pageSize = 5L;
        long startIndex = 1L;

        try (MockedStatic<CallContext> callContextStaticMock = Mockito.mockStatic(CallContext.class)) {
            callContextStaticMock.when(CallContext::current).thenReturn(callContextMock);
            Mockito.when(listIacResourceTypesCmdMock.getId()).thenReturn(iacResourceTypeId);
            Mockito.when(listIacResourceTypesCmdMock.getName()).thenReturn(iacResourceTypeName);
            Mockito.when(listIacResourceTypesCmdMock.getCategory()).thenReturn(iacResourceTypeCategory);
            Mockito.when(listIacResourceTypesCmdMock.showIacResourceTypeContent()).thenReturn(showIacResourceTypeContent);
            Mockito.when(listIacResourceTypesCmdMock.getKeyword()).thenReturn(iacResourceTypeKeyword);
            Mockito.when(listIacResourceTypesCmdMock.getPageSizeVal()).thenReturn(pageSize);
            Mockito.when(listIacResourceTypesCmdMock.getStartIndex()).thenReturn(startIndex);
            Mockito.when(iacResourceTypeDaoMock.listIacResourceTypes(Mockito.eq(iacResourceTypeId), Mockito.eq(iacResourceTypeName),
                            Mockito.eq(iacResourceTypeCategory), Mockito.eq(iacResourceTypeKeyword), Mockito.eq(pageSize), Mockito.eq(startIndex)))
                    .thenReturn(iacResourceTypesMock);

            Mockito.when(toscaOrchestratorMock.getNodeTypeApis(Mockito.any())).thenReturn(new Pair<>("provisioningApi", "rollbackApi"));
            Mockito.when(apiDiscoveryServiceMock.listApis(Mockito.any(), Mockito.anyString())).thenReturn(new ListResponse<>());

            List<IacResourceTypeResponse> iacResourceTypeResponsesMock = List.of(Mockito.mock(IacResourceTypeResponse.class), Mockito.mock(IacResourceTypeResponse.class));
            Mockito.when(nimbleResponseBuilderMock.createIacResourceTypeResponse(Mockito.eq(iacResourceTypesMock.get(0)), Mockito.eq(showIacResourceTypeContent))).thenReturn(iacResourceTypeResponsesMock.get(0));
            Mockito.when(nimbleResponseBuilderMock.createIacResourceTypeResponse(Mockito.eq(iacResourceTypesMock.get(1)), Mockito.eq(showIacResourceTypeContent))).thenReturn(iacResourceTypeResponsesMock.get(1));

            ListResponse<IacResourceTypeResponse> response = nimbleServiceSpy.listIacResourceTypes(listIacResourceTypesCmdMock);
            Assert.assertEquals(iacResourceTypeResponsesMock, response.getResponses());
            Assert.assertEquals(iacResourceTypeResponsesMock.size(), response.getCount().intValue());
        }
    }

    @Test
    public void listIacResourceTypesTestShouldNotReturnNodeTypesIfUserDoesNotHavePermissionToAccessItsApis() {
        try (MockedStatic<CallContext> callContextStaticMock = Mockito.mockStatic(CallContext.class)) {
            callContextStaticMock.when(CallContext::current).thenReturn(callContextMock);
            Mockito.when(iacResourceTypeDaoMock.listIacResourceTypes(Mockito.any(), Mockito.any(),
                            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(iacResourceTypesMock);

            Mockito.when(toscaOrchestratorMock.getNodeTypeApis(Mockito.any())).thenReturn(new Pair<>("provisioningApi", "rollbackApi"));
            Mockito.when(apiDiscoveryServiceMock.listApis(Mockito.any(), Mockito.eq("rollbackApi"))).thenReturn(null);

            ListResponse<IacResourceTypeResponse> response = nimbleServiceSpy.listIacResourceTypes(listIacResourceTypesCmdMock);
            Mockito.verify(nimbleResponseBuilderMock, Mockito.never()).createIacResourceTypeResponse(Mockito.any(), Mockito.anyBoolean());
            Assert.assertEquals(0, response.getCount().intValue());
        }
    }
}

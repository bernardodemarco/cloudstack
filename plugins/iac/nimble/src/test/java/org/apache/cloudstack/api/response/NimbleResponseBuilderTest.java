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
package org.apache.cloudstack.api.response;

import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeVO;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NimbleResponseBuilderTest {
    @Spy
    @InjectMocks
    private NimbleResponseBuilder nimbleResponseBuilderSpy;

    @Mock
    private IacResourceTypeVO iacResourceTypeVOMock;

    private IacResourceTypeResponse getExpectedIacResourceTypeResponse() {
        IacResourceTypeResponse iacResourceTypeResponse = new IacResourceTypeResponse();
        iacResourceTypeResponse.setId("uuid");
        iacResourceTypeResponse.setName("Resource Type Name");
        iacResourceTypeResponse.setIacResourceTypeContent("node_type: ResourceTypeName");
        return iacResourceTypeResponse;
    }

    @Test
    public void createIacResourceTypeResponseTestShouldPopulateAllFieldsOfTheResponse() {
        IacResourceTypeResponse expected = getExpectedIacResourceTypeResponse();

        Mockito.when(iacResourceTypeVOMock.getUuid()).thenReturn(expected.getId());
        Mockito.when(iacResourceTypeVOMock.getName()).thenReturn(expected.getName());
        Mockito.when(iacResourceTypeVOMock.getContent()).thenReturn(expected.getIacResourceTypeContent());

        IacResourceTypeResponse actual = nimbleResponseBuilderSpy.createIacResourceTypeResponse(iacResourceTypeVOMock);
        Assert.assertEquals(expected.getId(), actual.getId());
        Assert.assertEquals(expected.getName(), actual.getName());
        Assert.assertEquals(expected.getIacResourceTypeContent(), actual.getIacResourceTypeContent());
    }
}

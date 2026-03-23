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
package org.apache.cloudstack.api.command;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.service.NimbleService;

import javax.inject.Inject;

public class DeployIacTemplateCmd extends BaseAsyncCmd {
    @Inject
    private NimbleService nimbleService;

    @Parameter(name = ApiConstants.IAC_RESOURCE_TYPE_CONTENT, type = CommandType.STRING, length = 65535, description = "")
    private String iacTemplateContent;

    @Override
    public void execute() {
        nimbleService.deployIacTemplate(iacTemplateContent);
    }

    @Override
    public String getEventType() {
        return "";
    }

    @Override
    public String getEventDescription() {
        return "";
    }

    @Override
    public long getEntityOwnerId() {
        return 0;
    }
}

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

import com.cloud.user.Account;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.IacTemplateGraphResponse;
import org.apache.cloudstack.api.response.IacTemplateResponse;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;
import org.apache.cloudstack.service.NimbleService;

import javax.inject.Inject;

@APICommand(name = "planIacTemplateDeployment", description = "Plans the deployment of a registered IaC template.", responseObject = IacTemplateGraphResponse.class, entityType = {IacTemplate.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false, authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class PlanIacTemplateDeploymentCmd extends BaseCmd {
    @Inject
    private NimbleService nimbleService;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, required = true, entityType = IacTemplateResponse.class, description = "ID of the IaC template whose deployment will be planned.")
    private Long id;

    public Long getId() {
        return id;
    }

    @Override
    public void execute() {
        IacTemplateGraphResponse response = nimbleService.planIacTemplateDeployment(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}

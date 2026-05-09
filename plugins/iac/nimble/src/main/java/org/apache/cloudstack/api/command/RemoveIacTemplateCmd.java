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
import org.apache.cloudstack.api.ACL;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.IacTemplateResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;
import org.apache.cloudstack.service.NimbleService;

import javax.inject.Inject;

@APICommand(name = "removeIacTemplate", description = "Removes an existing IaC template.",
        responseObject = SuccessResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        entityType = {IacTemplate.class}, authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class RemoveIacTemplateCmd extends BaseCmd {
    @Inject
    private NimbleService nimbleService;

    @ACL
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = IacTemplateResponse.class, required = true, description = "ID of the IaC template to be removed.")
    private Long id;

    public Long getId() {
        return id;
    }

    @Override
    public long getEntityOwnerId() {
        IacTemplate iacTemplate = nimbleService.findIacTemplateById(id);
        if (iacTemplate == null) {
            return Account.ACCOUNT_ID_SYSTEM;
        }
        return iacTemplate.getAccountId();
    }

    @Override
    public void execute() {
        nimbleService.removeIacTemplate(this);
        SuccessResponse response = new SuccessResponse(getCommandName());
        setResponseObject(response);
    }
}

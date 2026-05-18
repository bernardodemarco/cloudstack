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
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.IacTemplateResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;

@APICommand(name = "registerIacTemplate",
        description = "Registers an IaC template, such as a TOSCA service template.",
        responseObject = IacTemplateResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        entityType = {IacTemplate.class}, authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class RegisterIacTemplateCmd extends BaseIacTemplateRegistrationCmd {
    @Parameter(name = ApiConstants.DESCRIPTION, type = CommandType.STRING, description = "Description of the IaC template.", length = 4096)
    private String description;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class,
            description = "ID of the domain associated with the IaC template. It must be used along with the \"account\" parameter.")
    private Long domainId;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING,
            description = "Name of the account that will own the IaC template. It must be used along with the \"domainid\" parameter.")
    private String accountName;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class,
            description = "ID of the project that will own the IaC template. Mutually exclusive with the \"account\" parameter.")
    private Long projectId;

    public String getDescription() {
        return description;
    }

    public Long getDomainId() {
        return domainId;
    }

    public String getAccountName() {
        return accountName;
    }

    public Long getProjectId() {
        return projectId;
    }

    @Override
    public long getEntityOwnerId() {
        Account caller = CallContext.current().getCallingAccount();
        Account owner = _accountService.finalizeOwner(caller, getAccountName(), getDomainId(), getProjectId());
        return owner == null ? caller.getAccountId() : owner.getAccountId();
    }

    @Override
    public void execute() {
        IacTemplateResponse response = nimbleService.saveIacTemplate(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}

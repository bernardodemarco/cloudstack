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
import org.apache.cloudstack.api.ApiArgValidator;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.IacTemplateResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;
import org.apache.cloudstack.service.NimbleService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@APICommand(name = "registerIacTemplate",
        description = "Registers an IaC template, such as a TOSCA service template.",
        responseObject = IacTemplateResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        entityType = {IacTemplate.class}, authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class RegisterIacTemplateCmd extends BaseCmd {
    @Inject
    private NimbleService nimbleService;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING,
            description = "Name of the IaC template.", required = true, validations = {ApiArgValidator.NotNullOrEmpty})
    private String name;

    @Parameter(name = ApiConstants.DESCRIPTION, type = CommandType.STRING, description = "Description of the IaC template.")
    private String description;

    @Parameter(name = ApiConstants.IAC_TEMPLATE_CONTENT, type = CommandType.STRING, length = 65535,
            description = "Content of the IaC template.", required = true, validations = {ApiArgValidator.NotNullOrEmpty})
    private String iacTemplateContent;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class,
            description = "ID of the domain associated with the IaC template. It must be used along with the \"account\" parameter.")
    private Long domainId;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING,
            description = "Name of the account that will own the IaC template. It must be used along with the \"domainid\" parameter.")
    private String accountName;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class,
            description = "ID of the project that will own the IaC template. Mutually exclusive with the \"account\" parameter.")
    private Long projectId;

    @Parameter(name = ApiConstants.SHARED_DOMAIN_IDS, type = CommandType.LIST, collectionType = CommandType.UUID, entityType = DomainResponse.class,
            description = "A comma-separated list of domain IDs with which the IaC template will be shared.")
    private List<Long> sharedDomainIds;

    @Parameter(name = ApiConstants.SHARED_ACCOUNT_IDS, type = CommandType.LIST, collectionType = CommandType.UUID, entityType = AccountResponse.class,
            description = "A comma-separated list of account IDs with which the IaC template will be shared.")
    private List<Long> sharedAccountIds;

    @Parameter(name = ApiConstants.SHARED_PROJECT_IDS, type = CommandType.LIST, collectionType = CommandType.UUID, entityType = ProjectResponse.class,
            description = "A comma-separated list of project IDs with which the IaC template will be shared.")
    private List<Long> sharedProjectIds;

    @Parameter(name = ApiConstants.RECURSIVE_DOMAINS, type = CommandType.BOOLEAN,
            description = "Defines whether the IaC template will be shared with the subdomains of the domains specified in the \"shareddomainids\" parameter. Defaults to false.")
    private boolean recursiveDomains = false;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIacTemplateContent() {
        return iacTemplateContent;
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

    public boolean isTemplateShared() {
        return sharedDomainIds != null || sharedAccountIds != null || sharedProjectIds != null;
    }

    public List<Long> getSharedDomainIds() {
        if (sharedDomainIds == null) {
            return new ArrayList<>();
        }
        return sharedDomainIds;
    }

    public List<Long> getSharedAccountIds() {
        if (sharedAccountIds == null) {
            return new ArrayList<>();
        }
        return sharedAccountIds;
    }

    public List<Long> getSharedProjectIds() {
        if (sharedProjectIds == null) {
            return new ArrayList<>();
        }
        return sharedProjectIds;
    }

    public boolean isRecursiveDomains() {
        return recursiveDomains;
    }

    @Override
    public long getEntityOwnerId() {
        Account caller = CallContext.current().getCallingAccount();
        Account owner = _accountService.finalizeOwner(caller, getAccountName(), getDomainId(), getProjectId());
        return owner == null ? caller.getAccountId() : owner.getAccountId();
    }

    @Override
    public void execute() {
        IacTemplateResponse response = nimbleService.registerIacTemplate(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}

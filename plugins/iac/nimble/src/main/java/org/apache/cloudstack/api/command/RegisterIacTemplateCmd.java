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

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.IacTemplateResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;
import org.apache.cloudstack.service.NimbleService;

import javax.inject.Inject;

@APICommand(name = "registerIacTemplate",
        description = "Register an IaC template, such as a TOSCA service template.",
        responseObject = IacTemplateResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        entityType = {IacTemplate.class}, authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class RegisterIacTemplateCmd extends BaseCmd {
    @Inject
    private NimbleService nimbleService;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "The name of the IaC template.", required = true)
    private String name;

    @Parameter(name = ApiConstants.DESCRIPTION, type = CommandType.STRING, description = "The description of the IaC template.")
    private String description;

    @Parameter(name = ApiConstants.IAC_TEMPLATE_CONTENT, type = CommandType.STRING, length = 65535, description = "The content of the IaC template.", required = true)
    private String iacTemplateContent;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class, description = "ID of the domain associated with the IaC template. It must be used along with the \"account\" parameter.")
    private Long domainId;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "The name of the account owner of the IaC template. It must be used along with the \"domainid\" parameter.")
    private String accountName;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "ID of the project owner of the IaC template. Mutually exclusive with the \"account\" parameter.")
    private Long projectId;

    @Parameter(name = ApiConstants.SHARED_DOMAIN_IDS, type = CommandType.STRING, description = "")
    private String sharedDomainIds;

    @Parameter(name = ApiConstants.SHARED_ACCOUNT_IDS, type = CommandType.STRING, description = "")
    private String sharedAccountIds;

    @Parameter(name = ApiConstants.SHARED_PROJECT_IDS, type = CommandType.STRING, description = "")
    private String sharedProjectIds;

    @Parameter(name = ApiConstants.RECURSIVE_DOMAINS, type = CommandType.BOOLEAN, description = "Defines whether IaC template will be shared among the subdomains of the informed domains in the \"shareddomainids\" parameter. Defaults to false.")
    private boolean recursiveDomains = false;
    
    @Override
    public void execute() {

    }

    @Override
    public long getEntityOwnerId() {
        return 0;
    }
}

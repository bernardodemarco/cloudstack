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
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.service.NimbleService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseIacTemplateRegistrationCmd extends BaseCmd {
    @Inject
    protected NimbleService nimbleService;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "Name of the IaC template.")
    private String name;

    @Parameter(name = ApiConstants.IAC_TEMPLATE_CONTENT, type = CommandType.STRING, length = 65535, description = "Content of the IaC template.")
    private String iacTemplateContent;

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
    private Boolean recursiveDomains;

    public String getName() {
        return name;
    }

    public String getIacTemplateContent() {
        return iacTemplateContent;
    }

    public boolean isTemplateShared() {
        return sharedDomainIds != null || sharedAccountIds != null || sharedProjectIds != null;
    }

    public List<Long> getSharedDomainIds() {
        return sharedDomainIds;
    }

    public List<Long> getSharedAccountIds() {
        return sharedAccountIds;
    }

    public List<Long> getSharedProjectIds() {
        if (sharedProjectIds == null) {
            return new ArrayList<>();
        }
        return sharedProjectIds;
    }

    public Boolean isRecursiveDomains() {
        return recursiveDomains;
    }
}

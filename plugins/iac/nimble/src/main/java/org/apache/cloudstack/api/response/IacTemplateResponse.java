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

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;

import java.util.Date;
import java.util.List;

@EntityReference(value = {IacTemplate.class})
public class IacTemplateResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the IaC template.")
    private String id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "Name of the IaC template.")
    private String name;

    @SerializedName(ApiConstants.DESCRIPTION)
    @Param(description = "Description of the IaC template.")
    private String description;

    @SerializedName(ApiConstants.IAC_TEMPLATE_CONTENT)
    @Param(description = "Content of the IaC template.")
    private String iacTemplateContent;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "ID of the domain of the IaC template owner.")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "Name of the domain of the IaC template owner.")
    private String domainName;

    @SerializedName(ApiConstants.DOMAIN_PATH)
    @Param(description = "Path of the domain of the IaC template owner.")
    private String domainPath;

    @SerializedName(ApiConstants.PROJECT_ID)
    @Param(description = "ID of the project owner of the IaC template.")
    private String projectId;

    @SerializedName(ApiConstants.PROJECT)
    @Param(description = "Name of the project owner of the IaC template.")
    private String projectName;

    @SerializedName(ApiConstants.ACCOUNT_ID)
    @Param(description = "ID of the account owner of the IaC template.")
    private String accountId;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "Name of the account owner of the IaC template.")
    private String accountName;

    @SerializedName(ApiConstants.RECURSIVE_DOMAINS)
    @Param(description = "Indicates whether the IaC template is shared with the subdomains of the domains specified in the \"shareddomainids\" field.")
    private Boolean recursiveDomains;

    @SerializedName(ApiConstants.CREATED)
    @Param(description = "Date when the IaC template was registered.")
    private Date created;

    @SerializedName(ApiConstants.REMOVED)
    @Param(description = "Date when the IaC template was removed.")
    private Date removed;

    @SerializedName("shareddomains")
    @Param(description = "Domains with which the IaC template is shared.")
    private List<SharedDomainResponse> sharedDomains;

    @SerializedName("sharedaccounts")
    @Param(description = "Accounts with which the IaC template is shared.")
    private List<SharedAccountResponse> sharedAccounts;

    @SerializedName("sharedprojects")
    @Param(description = "Projects with which the IaC template is shared.")
    private List<SharedProjectResponse> sharedProjects;

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIacTemplateContent(String iacTemplateContent) {
        this.iacTemplateContent = iacTemplateContent;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public void setDomainPath(String domainPath) {
        this.domainPath = domainPath;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setRecursiveDomains(Boolean recursiveDomains) {
        this.recursiveDomains = recursiveDomains;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setRemoved(Date removed) {
        this.removed = removed;
    }

    public void setSharedDomains(List<SharedDomainResponse> sharedDomains) {
        this.sharedDomains = sharedDomains;
    }

    public void setSharedAccounts(List<SharedAccountResponse> sharedAccounts) {
        this.sharedAccounts = sharedAccounts;
    }

    public void setSharedProjects(List<SharedProjectResponse> sharedProjects) {
        this.sharedProjects = sharedProjects;
    }

    //    LBHealthCheckPolicyResponse -> example
    public static class SharedDomainResponse {
        @SerializedName(ApiConstants.DOMAIN_ID)
        @Param(description = "ID of the domain with which the IaC template is shared.")
        private String domainId;

        @SerializedName(ApiConstants.DOMAIN)
        @Param(description = "Name of the domain with which the IaC template is shared.")
        private String domainName;

        @SerializedName(ApiConstants.DOMAIN_PATH)
        @Param(description = "Path of the domain with which the IaC template is shared.")
        private String domainPath;

        public void setDomainId(String domainId) {
            this.domainId = domainId;
        }

        public void setDomainName(String domainName) {
            this.domainName = domainName;
        }

        public void setDomainPath(String domainPath) {
            this.domainPath = domainPath;
        }
    }

    public static class SharedAccountResponse {
        @SerializedName(ApiConstants.ACCOUNT_ID)
        @Param(description = "ID of the account with which the IaC template is shared.")
        private String accountId;

        @SerializedName(ApiConstants.ACCOUNT)
        @Param(description = "Name of the account with which the IaC template is shared.")
        private String accountName;

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public void setAccountName(String accountName) {
            this.accountName = accountName;
        }
    }

    public static class SharedProjectResponse {
        @SerializedName(ApiConstants.PROJECT_ID)
        @Param(description = "ID of the project with which the IaC template is shared.")
        private String projectId;

        @SerializedName(ApiConstants.PROJECT)
        @Param(description = "Name of the project with which the IaC template is shared.")
        private String projectName;

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }
    }
}

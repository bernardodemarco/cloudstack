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

import com.cloud.api.ApiDBUtils;
import com.cloud.domain.Domain;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.projects.Project;
import com.cloud.projects.ProjectManager;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.utils.Pair;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;
import org.apache.cloudstack.persistence.iactemplates.IacTemplateAccountMapVO;
import org.apache.cloudstack.persistence.iactemplates.IacTemplateDomainMapVO;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceType;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;
import org.apache.cloudstack.tosca.model.ToscaServiceTemplate;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class NimbleResponseBuilder {
    @Inject
    private AccountService accountService;

    @Inject
    private ProjectManager projectManager;

    public IacResourceTypeResponse createIacResourceTypeResponse(IacResourceType iacResourceType, boolean showIacResourceTypeContent) {
        IacResourceTypeResponse response = new IacResourceTypeResponse();
        response.setId(iacResourceType.getUuid());
        response.setName(iacResourceType.getName());
        response.setCategory(iacResourceType.getCategory());

        if (showIacResourceTypeContent) {
            response.setIacResourceTypeContent(iacResourceType.getContent());
        }

        response.setObjectName("iacresourcetypes");
        return response;
    }

    public IacTemplateResponse createIacTemplateResponse(IacTemplate iacTemplate, boolean showIacTemplateContent) {
        IacTemplateResponse response = new IacTemplateResponse();
        response.setId(iacTemplate.getUuid());
        response.setName(iacTemplate.getName());
        response.setDescription(iacTemplate.getDescription());
        response.setCreated(iacTemplate.getCreated());
        response.setRemoved(iacTemplate.getRemoved());
        if (showIacTemplateContent) {
            response.setIacTemplateContent(iacTemplate.getIacTemplateContent());
        }

        Account caller = CallContext.current().getCallingAccount();
        Account owner = ApiDBUtils.findAccountById(iacTemplate.getAccountId());
        populateIacTemplateOwnerFields(response, caller, owner);
        populateIacTemplateSharedEntitiesFields(response, iacTemplate, caller, owner);

        response.setObjectName("iactemplates");
        return response;
    }

    private boolean verifyCallerAccessToIacTemplateOwner(Account caller, Account owner) {
        if (owner.getType() == Account.Type.PROJECT) {
            return projectManager.canAccessProjectAccount(caller, owner.getId());
        }

        try {
            accountService.checkAccess(caller, null, false, owner);
            return true;
        } catch (PermissionDeniedException ignored) {
            return false;
        }
    }

    private void populateIacTemplateOwnerFields(IacTemplateResponse response, Account caller, Account owner) {
        if (!verifyCallerAccessToIacTemplateOwner(caller, owner)) {
            return;
        }

        Domain domain = ApiDBUtils.findDomainById(owner.getDomainId());
        if (domain != null) {
            response.setDomainId(domain.getUuid());
            response.setDomainName(domain.getName());
            response.setDomainPath(domain.getPath());
        }

        if (owner.getType() == Account.Type.PROJECT) {
            Project project = ApiDBUtils.findProjectByProjectAccountIdIncludingRemoved(owner.getId());
            if (project != null) {
                response.setProjectId(project.getUuid());
                response.setProjectName(project.getName());
            }
        } else {
            response.setAccountName(owner.getAccountName());
            response.setAccountId(owner.getUuid());
        }
    }

    private void populateIacTemplateSharedEntitiesFields(IacTemplateResponse response, IacTemplate iacTemplate, Account caller, Account owner) {
        if (verifyCallerAccessToIacTemplateOwner(caller, owner)) {
            response.setRecursiveDomains(iacTemplate.isRecursiveDomains());
            response.setSharedDomains(getSharedDomainResponses(iacTemplate.getDomainMappings()));
            Pair<List<IacTemplateResponse.SharedAccountResponse>, List<IacTemplateResponse.SharedProjectResponse>> sharedAccountAndProjectResponses = getSharedAccountAndProjectResponses(iacTemplate.getAccountMappings());
            response.setSharedAccounts(sharedAccountAndProjectResponses.first());
            response.setSharedProjects(sharedAccountAndProjectResponses.second());
        }
    }

    private List<IacTemplateResponse.SharedDomainResponse> getSharedDomainResponses(List<IacTemplateDomainMapVO> domainMappings) {
        return domainMappings.stream().map(domainMapping -> {
            Domain domain = ApiDBUtils.findDomainById(domainMapping.getDomainId());
            if (domain == null) {
                return null;
            }

            return new IacTemplateResponse.SharedDomainResponse(domain.getUuid(), domain.getName(), domain.getPath());
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private Pair<List<IacTemplateResponse.SharedAccountResponse>, List<IacTemplateResponse.SharedProjectResponse>> getSharedAccountAndProjectResponses(List<IacTemplateAccountMapVO> accountMappings) {
        List<IacTemplateResponse.SharedAccountResponse> sharedAccountResponses = new ArrayList<>();
        List<IacTemplateResponse.SharedProjectResponse> sharedProjectResponses = new ArrayList<>();

        for (IacTemplateAccountMapVO accountMapping : accountMappings) {
            Project project = ApiDBUtils.findProjectByProjectAccountIdIncludingRemoved(accountMapping.getAccountId());
            if (project != null) {
                sharedProjectResponses.add(new IacTemplateResponse.SharedProjectResponse(project.getUuid(), project.getName()));
            } else {
                Account account = ApiDBUtils.findAccountById(accountMapping.getAccountId());
                if (account != null) {
                    sharedAccountResponses.add(new IacTemplateResponse.SharedAccountResponse(account.getUuid(), account.getAccountName()));
                }
            }
        }

        return new Pair<>(sharedAccountResponses, sharedProjectResponses);
    }

    public IacTemplateGraphResponse createIacTemplateGraphResponse(IacTemplate iacTemplate, ToscaServiceTemplate serviceTemplate) {
        Map<String, List<String>> graphOutput = new LinkedHashMap<>();
        Set<String> nodesWithOutDependencies = new HashSet<>(serviceTemplate.getNodeTemplates().keySet());
        nodesWithOutDependencies.removeAll(serviceTemplate.getDependencyGraph().keySet());
        nodesWithOutDependencies.forEach(node -> graphOutput.put(node, new ArrayList<>()));

        serviceTemplate.getDependencyGraph().forEach((node, dependencies) -> {
            List<String> dependenciesOutput = dependencies.stream().map(ToscaNodeTemplate::getName).collect(Collectors.toList());
            graphOutput.put(node, dependenciesOutput);
        });

        IacTemplateGraphResponse response = new IacTemplateGraphResponse(iacTemplate.getUuid(), graphOutput);
        response.setObjectName("iactemplategraph");
        return response;
    }
}

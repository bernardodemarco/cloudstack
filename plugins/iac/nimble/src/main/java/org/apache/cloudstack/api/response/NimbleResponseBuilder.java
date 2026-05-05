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
import com.cloud.exception.PermissionDeniedException;
import com.cloud.projects.Project;
import com.cloud.projects.ProjectManager;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceType;
import org.apache.commons.lang3.ObjectUtils;

import javax.inject.Inject;

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

    public IacTemplateResponse createIacTemplateResponse(IacTemplate iacTemplate) {
        IacTemplateResponse response = new IacTemplateResponse();
        response.setId(iacTemplate.getUuid());
        response.setName(iacTemplate.getName());
        response.setDescription(iacTemplate.getDescription());
        response.setIacTemplateContent(iacTemplate.getIacTemplateContent());
        response.setRecursiveDomains(iacTemplate.isRecursiveDomains());
        response.setCreated(iacTemplate.getCreated());
        response.setRemoved(iacTemplate.getRemoved());

        Account caller = CallContext.current().getCallingAccount();
        Account owner = ApiDBUtils.findAccountById(iacTemplate.getAccountId());
        populateIacTemplateOwnerFields(response, iacTemplate, caller, owner);
        response.setObjectName("iactemplates");
        return response;
    }

//    caller vai obter owner quando tem acesso ao projeto/conta do owner
    private void populateIacTemplateOwnerFields(IacTemplateResponse response, IacTemplate iacTemplate, Account caller, Account owner) {
        if (owner.getType() == Account.Type.PROJECT) {
            if (projectManager.canAccessProjectAccount(caller, owner.getId())) {
                Project project = ApiDBUtils.findProjectByProjectAccountId(owner.getId());
                response.setProjectId(project.getUuid());
                response.setProjectName(project.getName());
            }

            return;
        }

        try {
            accountService.checkAccess(caller, null, false, owner);
            response.setAccountName(owner.getAccountName());
            response.setAccountId(owner.getUuid());
        } catch (PermissionDeniedException ignored) {}
    }

    //    para entidades compartilhadas, vou colocar para ter acesso apenas quando
//    é root admin, admin e tem acesso ao owner ou é o owner
    private void populateIacTemplateSharedEntitiesFields(IacTemplateResponse response, IacTemplate iacTemplate, Account caller, Account owner) {
        boolean isCallerAdmin = accountService.isAdmin(caller.getId());
        boolean isCallerTheIacTemplateOwner = caller.getId() == owner.getId();
        if (!isCallerAdmin && !isCallerTheIacTemplateOwner) {
            return;
        }

        if (isCallerAdmin && !accountService.isRootAdmin(caller.getId())) {
            try {
                accountService.checkAccess(caller, null, false, owner);
            } catch (PermissionDeniedException e) {
                return;
            }
        }

//        has access
    }
}

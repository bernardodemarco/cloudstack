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

import com.cloud.exception.InvalidParameterValueException;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.IacResourceTypeResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceType;
import org.apache.cloudstack.service.NimbleService;
import org.apache.commons.lang3.EnumUtils;

import javax.inject.Inject;

@APICommand(name = "listIacResourceTypes",
        description = "Lists all available IaC resource types, such as TOSCA node types.",
        responseObject = IacResourceTypeResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        entityType = {IacResourceType.class}, authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class ListIacResourceTypesCmd extends BaseListCmd {
    @Inject
    private NimbleService nimbleService;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = IacResourceTypeResponse.class, description = "The ID of the IaC resource type.")
    private Long id;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "The name of the IaC resource type.")
    private String name;

    @Parameter(name = ApiConstants.CATEGORY, type = CommandType.STRING, description = "The category of the IaC resource type. " +
            "Valid values are: compute, storage, network, service_offering and cloud_access_management.")
    private String category;

    @Parameter(name = ApiConstants.SHOW_IAC_RESOURCE_TYPE_CONTENT, type = CommandType.BOOLEAN, description = "Whether to return the IaC resource type content. Defaults to false.")
    private boolean showIacResourceTypeContent = false;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public IacResourceType.Category getCategory() {
        if (category == null) {
            return null;
        }

        IacResourceType.Category iacResourceTypeCategory = EnumUtils.getEnumIgnoreCase(IacResourceType.Category.class, category);
        if (iacResourceTypeCategory == null) {
            throw new InvalidParameterValueException(
                    String.format("The IaC resource type category [%s] is invalid. Valid values are: compute, storage, network, service_offering and cloud_access_management.", category));
        }

        return iacResourceTypeCategory;
    }

    public boolean showIacResourceTypeContent() {
        return showIacResourceTypeContent;
    }

    @Override
    public void execute() {
        ListResponse<IacResourceTypeResponse> response = nimbleService.listIacResourceTypes(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}

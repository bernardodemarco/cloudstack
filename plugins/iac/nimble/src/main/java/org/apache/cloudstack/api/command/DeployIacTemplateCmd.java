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
import com.cloud.user.Account;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.IacTemplateDeploymentResponse;
import org.apache.cloudstack.api.response.IacTemplateResponse;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;
import org.apache.cloudstack.service.NimbleService;
import org.apache.commons.collections.MapUtils;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@APICommand(name = "deployIacTemplate", description = "Deploys an already registered IaC template.", responseObject = IacTemplateDeploymentResponse.class, entityType = {IacTemplate.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false, authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class DeployIacTemplateCmd extends BaseAsyncCmd {
    @Inject
    private NimbleService nimbleService;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, required = true, entityType = IacTemplateResponse.class, description = "ID of the IaC template to be deployed.")
    private Long id;

    @Parameter(name = ApiConstants.INPUTS, type = CommandType.MAP, description = "Input variables of the IaC template. They must be specified as key-pairs, for instance: 'inputs[0].first-input=\"First input value\" inputs[0].second-input=\"Second input value\"'")
    private Map<String, Map<String, String>> inputs;

    public Long getId() {
        return id;
    }

    public Map<String, String> getInputs() {
        if (MapUtils.isEmpty(inputs)) {
            return new HashMap<>();
        }

        if (inputs.size() > 1) {
            throw new InvalidParameterValueException("Please, specify the inputs as key-pairs, indexed with [0]. For instance: 'inputs[0].first-input=\"First input value\" inputs[0].second-input=\"Second input value\"'");
        }

        return inputs.values().iterator().next();
    }

    @Override
    public void execute() {
        IacTemplateDeploymentResponse response = nimbleService.deployIacTemplate(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getEventType() {
        return "";
    }

    @Override
    public String getEventDescription() {
        return "";
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}

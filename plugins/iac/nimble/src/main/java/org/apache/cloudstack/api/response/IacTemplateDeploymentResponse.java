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

import java.util.List;
import java.util.Map;

public class IacTemplateDeploymentResponse extends BaseResponse {
    @SerializedName(ApiConstants.SUCCESS)
    @Param(description = "Whether the IaC template deployment completed successfully.")
    private boolean success;

    @SerializedName("nodes")
    @Param(description = "The provisioning result of each node template.")
    private List<NodeTemplateDeploymentResponse> nodes;

    @SerializedName("deploymenterror")
    @Param(description = "IaC template deployment error.")
    private String deploymentError;

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setNodes(List<NodeTemplateDeploymentResponse> nodes) {
        this.nodes = nodes;
    }

    public void setDeploymentError(String deploymentError) {
        this.deploymentError = deploymentError;
    }

    public static class NodeTemplateDeploymentResponse {
        @SerializedName(ApiConstants.NAME)
        @Param(description = "Name of the node template.")
        private String name;

        @SerializedName(ApiConstants.TYPE)
        @Param(description = "Type of the IaC template node.")
        private String type;

        @SerializedName(ApiConstants.STATE)
        @Param(description = "Provisioning state of the node template.")
        private String state;

        @SerializedName(ApiConstants.PROPERTIES)
        @Param(description = "Evaluated properties of the node template.")
        private Map<String, String> properties;

        @SerializedName("attributes")
        @Param(description = "Attributes of the node template populated after provisioning.")
        private Map<String, String> attributes;

        @SerializedName("error")
        @Param(description = "Error message if the node template failed to be provisioned.")
        private String error;

        public void setName(String name) {
            this.name = name;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setState(String state) {
            this.state = state;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
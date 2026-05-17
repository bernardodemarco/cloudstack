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

public class IacTemplateGraphResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the IaC template.")
    private String id;

    @SerializedName("summary")
    @Param(description = "A summary of the IaC template topology.")
    private IacTemplateGraphSummary summary;

    @SerializedName("nodes")
    @Param(description = "Nodes of the graph representing the IaC template topology.")
    private Map<String, IacTemplateNodeResponse> nodes;

    public IacTemplateGraphResponse(String id, IacTemplateGraphSummary summary, Map<String, IacTemplateNodeResponse> nodes) {
        this.id = id;
        this.summary = summary;
        this.nodes = nodes;
    }

    public static class IacTemplateGraphSummary {
        @SerializedName("totalnodes")
        @Param(description = "Number of nodes in the IaC template graph.")
        private int totalNodes;

        @SerializedName("rootnodes")
        @Param(description = "Number of root nodes in the IaC template graph. Root nodes do not depend on any other nodes to be deployed.")
        private int rootNodes;

        @SerializedName("nodeswithdependencies")
        @Param(description = "Number of nodes in the IaC template graph with one or more dependencies.")
        private int nodesWithDependencies;

        public IacTemplateGraphSummary(int totalNodes, int rootNodes) {
            this.totalNodes = totalNodes;
            this.rootNodes = rootNodes;
            this.nodesWithDependencies = totalNodes - rootNodes;
        }
    }

    public static class IacTemplateNodeResponse {
        @SerializedName(ApiConstants.TYPE)
        @Param(description = "Type of the IaC template node.")
        private String type;

        @SerializedName("dependson")
        @Param(description = "Names of the nodes that this IaC template node depends on.")
        private List<String> dependencies;

        @SerializedName(ApiConstants.LEVEL)
        @Param(description = "The provisioning level of the node in the IaC template dependency graph. " +
                "Nodes at level 1 have no dependencies and are provisioned first. " +
                "Nodes sharing the same level have no dependencies on each other and may be provisioned in parallel. " +
                "A node at level N is only provisioned after all nodes at levels 1 through N-1 of its dependency chain have been successfully provisioned.")
        private int level;

        @SerializedName("totaldependencies")
        @Param(description = "Number of dependencies of the IaC template node.")
        private int dependenciesCount;

        public IacTemplateNodeResponse(String type, int level, List<String> dependencies) {
            this.type = type;
            this.level = level;
            this.dependencies = dependencies;
            this.dependenciesCount = dependencies.size();
        }
    }
}

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
package org.apache.cloudstack.service;

import com.cloud.utils.component.PluggableService;
import org.apache.cloudstack.api.command.ListIacResourceTypesCmd;
import org.apache.cloudstack.api.response.IacResourceTypeResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

public interface NimbleService extends PluggableService, Configurable {
    ConfigKey<Boolean> NimbleServiceEnabled = new ConfigKey<>("Advanced", Boolean.class,
            "nimble.service.enabled", "false",
            "Indicates whether NIMBLE (Native IaC Management, Build & Launch Engine) is enabled.", false);

    ConfigKey<Integer> NimbleServicePoolSize = new ConfigKey<>("Advanced", Integer.class, "nimble.service.pool.size", "1",
            "Number of threads in the global pool used to execute NIMBLE node template provisioning tasks. " +
                    "This pool is initialized during NIMBLE startup using the configured value. Administrators should " +
                    "tune this setting based on service utilization to optimize provisioning performance.",
            false, NimbleServiceEnabled.key());

    ListResponse<IacResourceTypeResponse> listIacResourceTypes(ListIacResourceTypesCmd cmd);
    void deployIacTemplate(String iacTemplateContent);
}

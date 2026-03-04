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

import com.cloud.utils.component.ManagerBase;
import org.apache.cloudstack.api.command.ListIacResourceTypesCmd;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacTemplatesProfile;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacTemplatesProfileDao;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.parser.ToscaParser;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class NimbleManagerImpl extends ManagerBase implements NimbleService {
    @Inject
    private ToscaParser toscaParser;

    @Inject
    private IacTemplatesProfileDao iacTemplatesProfileDao;

    private ExecutorService nimbleExecutorPool;

    private Map<String, ToscaNodeType> toscaProfile;

    @Override
    public void listIacResourceTypes() {
        logger.info("All set :) -> let's finish this!!!");
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);

        int nimbleServicePoolSize = NimbleService.NimbleServicePoolSize.value();
        logger.debug("Configuring NIMBLE's fixed thread pool with [{}] threads.", nimbleServicePoolSize);
        nimbleExecutorPool = Executors.newFixedThreadPool(nimbleServicePoolSize);
        toscaProfile = loadToscaProfile();
        return true;
    }

    protected Map<String, ToscaNodeType> loadToscaProfile() {
        logger.info("Loading NIMBLE's TOSCA profile.");
        List<IacTemplatesProfile> profileElements = iacTemplatesProfileDao.listAll();

        return profileElements.stream()
                .map(element -> toscaParser.parseNodeType(Paths.get(String.format("%s/%s", NIMBLE_PROFILE_FOLDER_PATH, element.getElementContentFilePath()))))
                .collect(Collectors.toMap(ToscaNodeType::getName, nodeType -> nodeType));
    }

    @Override
    public boolean stop() {
        logger.info("Stopping NIMBLE's manager.");

        if (nimbleExecutorPool != null) {
            logger.debug("Shutting down NIMBLE's fixed thread pool.");
            nimbleExecutorPool.shutdown();
        }

        return true;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> commands = new ArrayList<>();
        if (!NimbleServiceEnabled.value()) {
            return commands;
        }
        return List.of(ListIacResourceTypesCmd.class);
    }

    @Override
    public String getConfigComponentName() {
        return NimbleService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] { NimbleServiceEnabled, NimbleServicePoolSize };
    }
}

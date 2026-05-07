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

import com.cloud.domain.Domain;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.projects.Project;
import com.cloud.projects.ProjectManager;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.DomainManager;
import com.cloud.user.User;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.command.DeployIacTemplateCmd;
import org.apache.cloudstack.api.command.ListIacResourceTypesCmd;
import org.apache.cloudstack.api.command.RegisterIacTemplateCmd;
import org.apache.cloudstack.api.response.IacResourceTypeResponse;
import org.apache.cloudstack.api.response.IacTemplateResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NimbleResponseBuilder;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.discovery.ApiDiscoveryService;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;
import org.apache.cloudstack.persistence.iactemplates.IacTemplateAccountMapDao;
import org.apache.cloudstack.persistence.iactemplates.IacTemplateAccountMapVO;
import org.apache.cloudstack.persistence.iactemplates.IacTemplateDao;
import org.apache.cloudstack.persistence.iactemplates.IacTemplateDomainMapDao;
import org.apache.cloudstack.persistence.iactemplates.IacTemplateDomainMapVO;
import org.apache.cloudstack.persistence.iactemplates.IacTemplateVO;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeDao;
import org.apache.cloudstack.persistence.iactemplatesprofile.IacResourceTypeVO;
import org.apache.cloudstack.tosca.orchestrator.ToscaOrchestrator;
import org.apache.commons.lang3.ObjectUtils;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NimbleManagerImpl extends ManagerBase implements NimbleService {
    @Inject
    private ToscaOrchestrator toscaOrchestrator;

    @Inject
    private IacResourceTypeDao iacResourceTypeDao;

    @Inject
    private IacTemplateDao iacTemplateDao;

    @Inject
    private IacTemplateDomainMapDao iacTemplateDomainMapDao;

    @Inject
    private IacTemplateAccountMapDao iacTemplateAccountMapDao;

    @Inject
    private NimbleResponseBuilder responseBuilder;

    @Inject
    private ApiDiscoveryService apiDiscoveryService;

    @Inject
    private AccountService accountService;

    @Inject
    private DomainManager domainManager;

    @Inject
    private ProjectManager projectManager;

    @Override
    public ListResponse<IacResourceTypeResponse> listIacResourceTypes(ListIacResourceTypesCmd cmd) {
        List<IacResourceTypeVO> iacResourceTypes = iacResourceTypeDao.listIacResourceTypes(cmd.getId(), cmd.getName(),
                cmd.getCategory(), cmd.getKeyword(), cmd.getPageSizeVal(), cmd.getStartIndex());

        List<IacResourceTypeResponse> iacResourceTypeResponses = iacResourceTypes.stream()
                .filter(iacResourceTypeVO -> doesUserHaveAccessToNodeTypeApis(toscaOrchestrator.getNodeTypeApis(iacResourceTypeVO.getName())))
                .map(iacResourceType -> responseBuilder.createIacResourceTypeResponse(iacResourceType, cmd.showIacResourceTypeContent()))
                .collect(Collectors.toList());

        ListResponse<IacResourceTypeResponse> response = new ListResponse<>();
        response.setResponses(iacResourceTypeResponses, iacResourceTypeResponses.size());
        return response;
    }

    private boolean doesUserHaveAccessToNodeTypeApis(Pair<String, String> nodeTypeApis) {
        User callingUser = CallContext.current().getCallingUser();
        logger.trace("Checking if calling user [{}] has access to node type APIs [{}] and [{}].", callingUser, nodeTypeApis.first(), nodeTypeApis.second());
        return ObjectUtils.allNotNull(
                apiDiscoveryService.listApis(callingUser, nodeTypeApis.first()),
                apiDiscoveryService.listApis(callingUser, nodeTypeApis.second())
        );
    }

    @Override
    public IacTemplateResponse registerIacTemplate(RegisterIacTemplateCmd cmd) {
        Account owner = accountService.getActiveAccountById(cmd.getEntityOwnerId());
        boolean isTemplateOwnerAdmin = accountService.isAdmin(owner.getId());
        if (!isTemplateOwnerAdmin) {
            if (cmd.isRecursiveDomains()) {
                throw new InvalidParameterValueException(String.format("An IaC template owned by [%s] cannot be shared recursively across different domains.", owner.getAccountName()));
            }

            if (cmd.isTemplateShared()) {
                throw new InvalidParameterValueException(String.format("Account [%s] does not have permission to share IaC template with other entities.", owner.getAccountName()));
            }
        }

//        validateAccessToIacTemplateSharingEntities(owner, cmd);
        toscaOrchestrator.parseServiceTemplate(cmd.getIacTemplateContent());

        IacTemplate iacTemplate = persistIacTemplate(cmd, owner);
        if (iacTemplate == null) {
            throw new CloudRuntimeException("Unable to register IaC template.");
        }
        return responseBuilder.createIacTemplateResponse(iacTemplate, false);
    }

    private IacTemplate persistIacTemplate(RegisterIacTemplateCmd cmd, Account owner) {
        IacTemplateVO iacTemplate = new IacTemplateVO(cmd.getName(), cmd.getDescription(), cmd.getIacTemplateContent(),
                cmd.isRecursiveDomains(), owner.getDomainId(), owner.getAccountId());
        return Transaction.execute((TransactionCallback<IacTemplate>) (status) -> {
            IacTemplateVO persistedTemplate = iacTemplateDao.persist(iacTemplate);
            List<IacTemplateDomainMapVO> domainMappings = persistDomainMappings(cmd.getSharedDomainIds(), persistedTemplate.getId());
            List<IacTemplateAccountMapVO> accountMappings = persistAccountMappings(cmd.getSharedAccountIds(), cmd.getSharedProjectIds(), persistedTemplate.getId(), owner);
            persistedTemplate.setDomainMappings(domainMappings);
            persistedTemplate.setAccountMappings(accountMappings);
            return persistedTemplate;
        });
    }

    private List<IacTemplateAccountMapVO> persistAccountMappings(List<Long> sharedAccountIds, List<Long> sharedProjectIds, long iacTemplateId, Account iacTemplateOwner) {
        List<IacTemplateAccountMapVO> accountMappings = new ArrayList<>();

        for (Long accountId : sharedAccountIds) {
            Account account = accountService.getActiveAccountById(accountId);
            if (account == null) {
                throw new InvalidParameterValueException(String.format("Unable to find account with ID [%s].", accountId));
            }
            accountService.checkAccess(iacTemplateOwner, null, false, account);
            IacTemplateAccountMapVO accountMapping = new IacTemplateAccountMapVO(iacTemplateId, accountId);
            iacTemplateAccountMapDao.persist(accountMapping);
            accountMappings.add(accountMapping);
        }

        for (Long projectId : sharedProjectIds) {
            Project project = projectManager.getProject(projectId);
            if (project == null) {
                throw new InvalidParameterValueException(String.format("Unable to find project with ID [%s].", projectId));
            }
            if (!projectManager.canAccessProjectAccount(iacTemplateOwner, project.getProjectAccountId())) {
                throw new InvalidParameterValueException(String.format("Account [%s] does not have permission to share IaC template with project [%s].", iacTemplateOwner.getAccountName(), project.getName()));
            }
            IacTemplateAccountMapVO accountMapping = new IacTemplateAccountMapVO(iacTemplateId, project.getProjectAccountId());
            iacTemplateAccountMapDao.persist(accountMapping);
            accountMappings.add(accountMapping);
        }

        return accountMappings;
    }

    private List<IacTemplateDomainMapVO> persistDomainMappings(List<Long> sharedDomainIds, long iacTemplateId) {
        return sharedDomainIds.stream()
                .map(domainId -> {
                    IacTemplateDomainMapVO domainMapping = new IacTemplateDomainMapVO(iacTemplateId, domainId);
                    iacTemplateDomainMapDao.persist(domainMapping);
                    return domainMapping;
                }).collect(Collectors.toList());
    }

    protected void validateAccessToIacTemplateSharingEntities(Account owner, RegisterIacTemplateCmd cmd) {

        cmd.getSharedDomainIds().forEach(domainId -> {
            Domain domain = domainManager.getDomain(domainId);
            if (domain == null) {
                throw new InvalidParameterValueException(String.format("Unable to find domain with ID [%s].", domainId));
            }
            accountService.checkAccess(owner, domain);
        });

        cmd.getSharedAccountIds().forEach(accountId -> {
            Account account = accountService.getActiveAccountById(accountId);
            if (account == null) {
                throw new InvalidParameterValueException(String.format("Unable to find account with ID [%s].", accountId));
            }
            accountService.checkAccess(owner, null, false, account);
        });

        cmd.getSharedProjectIds().forEach(projectId -> {
            Project project = projectManager.getProject(projectId);
            if (project == null) {
                throw new InvalidParameterValueException(String.format("Unable to find project with ID [%s].", projectId));
            }
            projectManager.canAccessProjectAccount(owner, project.getProjectAccountId());
        });
    }

    @Override
    public void deployIacTemplate(String iacTemplateContent, Map<String, String> inputs) {
        toscaOrchestrator.deployIacTemplate(iacTemplateContent, inputs);
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);

        int nimbleServicePoolSize = NimbleService.NimbleServicePoolSize.value();
        toscaOrchestrator.configureExecutorPool(nimbleServicePoolSize);
        toscaOrchestrator.loadToscaProfile(iacResourceTypeDao.listAll());
        return true;
    }

    @Override
    public boolean stop() {
        logger.info("Stopping NIMBLE's manager.");
        toscaOrchestrator.shutdownExecutorPool();
        return true;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> commands = new ArrayList<>();
        if (!NimbleServiceEnabled.value()) {
            return commands;
        }
        return List.of(ListIacResourceTypesCmd.class, RegisterIacTemplateCmd.class, DeployIacTemplateCmd.class);
    }

    @Override
    public String getConfigComponentName() {
        return NimbleService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                NimbleServiceEnabled, NimbleServicePoolSize, NimbleIaCTemplateExecutionTimeout, NimbleNodeProvisioningTaskCheckInterval
        };
    }
}

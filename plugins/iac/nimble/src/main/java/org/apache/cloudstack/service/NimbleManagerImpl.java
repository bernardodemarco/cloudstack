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
import com.cloud.exception.PermissionDeniedException;
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
import org.apache.cloudstack.acl.ControlledEntity;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.command.BaseIacTemplateRegistrationCmd;
import org.apache.cloudstack.api.command.DeployIacTemplateCmd;
import org.apache.cloudstack.api.command.ListIacResourceTypesCmd;
import org.apache.cloudstack.api.command.RegisterIacTemplateCmd;
import org.apache.cloudstack.api.command.RemoveIacTemplateCmd;
import org.apache.cloudstack.api.command.UpdateIacTemplateCmd;
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
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

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
    public IacTemplateResponse saveIacTemplate(BaseIacTemplateRegistrationCmd cmd) {
        boolean iacTemplateUpdate = cmd instanceof UpdateIacTemplateCmd;
        if (iacTemplateUpdate) {
            IacTemplate iacTemplate = iacTemplateDao.findById(((UpdateIacTemplateCmd) cmd).getId());
            if (iacTemplate == null) {
                throw new InvalidParameterValueException("Unable to find IaC template with the specified ID.");
            }
        } else {
            if (StringUtils.isBlank(cmd.getIacTemplateContent()) || StringUtils.isBlank(cmd.getName())) {
                throw new InvalidParameterValueException(String.format("The [%s] parameter must be specified. It must not be an empty string.",
                        StringUtils.isBlank(cmd.getIacTemplateContent()) ? ApiConstants.IAC_TEMPLATE_CONTENT : ApiConstants.NAME));
            }
        }

        Account owner = accountService.getActiveAccountById(cmd.getEntityOwnerId());
        verifyOwnerPermissionToShareIacTemplates(owner, cmd.isTemplateShared(), BooleanUtils.toBoolean(cmd.isRecursiveDomains()));
        if (StringUtils.isNotBlank(cmd.getIacTemplateContent())) {
            toscaOrchestrator.parseServiceTemplate(cmd.getIacTemplateContent());
        }

        IacTemplate iacTemplate = persistIacTemplate(cmd, owner, iacTemplateUpdate);
        if (iacTemplate == null) {
            throw new CloudRuntimeException(String.format("Unable to %s IaC template.", iacTemplateUpdate ? "update" : "register"));
        }
        return responseBuilder.createIacTemplateResponse(iacTemplate, false);
    }

    private void verifyOwnerPermissionToShareIacTemplates(Account owner, boolean isTemplateShared, boolean isRecursiveDomains) {
        boolean iacTemplateBelongsToProject = owner.getType() == Account.Type.PROJECT;
        if (!accountService.isAdmin(owner.getId()) || iacTemplateBelongsToProject) {
            if (isTemplateShared) {
                if (iacTemplateBelongsToProject) {
                    throw new InvalidParameterValueException("IaC templates owned by projects cannot be shared with other entities.");
                }
                throw new InvalidParameterValueException(String.format("Account [%s] does not have permission to share IaC template with other entities.", owner.getAccountName()));
            }

            if (isRecursiveDomains) {
                if (iacTemplateBelongsToProject) {
                    throw new InvalidParameterValueException("IaC templates owned by projects cannot be shared recursively across different domains.");
                }
                throw new InvalidParameterValueException(String.format("An IaC template owned by [%s] cannot be shared recursively across different domains.", owner.getAccountName()));
            }
        }
    }

    private IacTemplateVO getUpdatedIacTemplate(UpdateIacTemplateCmd cmd) {
        IacTemplateVO iacTemplate = iacTemplateDao.findById(cmd.getId());

        if (StringUtils.isNotBlank(cmd.getName())) {
            iacTemplate.setName(cmd.getName());
        }

        if (StringUtils.isNotBlank(cmd.getDescription())) {
            iacTemplate.setDescription(cmd.getDescription());
        }

        if (StringUtils.isNotBlank(cmd.getIacTemplateContent())) {
            iacTemplate.setIacTemplateContent(cmd.getIacTemplateContent());
        }

        if (cmd.isRecursiveDomains() != null) {
            iacTemplate.setRecursiveDomains(cmd.isRecursiveDomains());
        }

        return iacTemplate;
    }

    private IacTemplateVO getNewIacTemplate(RegisterIacTemplateCmd cmd, Account owner) {
        return new IacTemplateVO(cmd.getName(), cmd.getDescription(), cmd.getIacTemplateContent(),
                BooleanUtils.toBoolean(cmd.isRecursiveDomains()), owner.getDomainId(), owner.getAccountId());
    }

    private IacTemplate persistIacTemplate(BaseIacTemplateRegistrationCmd cmd, Account owner, boolean iacTemplateUpdate) {
        IacTemplateVO iacTemplate = iacTemplateUpdate ? getUpdatedIacTemplate((UpdateIacTemplateCmd) cmd)
                : getNewIacTemplate((RegisterIacTemplateCmd) cmd, owner);

        return Transaction.execute((TransactionCallback<IacTemplate>) (status) -> {
            IacTemplateVO persistedTemplate = iacTemplateDao.persist(iacTemplate);

            if (cmd.getSharedDomainIds() != null) {
                if (iacTemplateUpdate) {
                    iacTemplateDomainMapDao.removeByIacTemplateId(iacTemplate.getId());
                }
//                convert to sets -> remove duplicates
                List<IacTemplateDomainMapVO> domainMappings = persistDomainMappings(cmd.getSharedDomainIds(), persistedTemplate.getId(), owner);
                persistedTemplate.setDomainMappings(domainMappings);
            }

//            add project flag to the iactemplatwaccountmapvo -> if not, when updating and removing only projects or accoutns, all of them will be removed
            if (cmd.getSharedAccountIds() != null) {
                if (iacTemplateUpdate) {
                    iacTemplateAccountMapDao.removeByIacTemplateId(iacTemplate.getId());
                }
                List<IacTemplateAccountMapVO> accountMappings = persistAccountMappings(cmd.getSharedAccountIds(), cmd.getSharedProjectIds(), persistedTemplate.getId(), owner);
                persistedTemplate.setAccountMappings(accountMappings);
            }
            return persistedTemplate;
        });
    }

    private List<IacTemplateDomainMapVO> persistDomainMappings(List<Long> sharedDomainIds, long iacTemplateId, Account iacTemplateOwner) {
        List<IacTemplateDomainMapVO> domainMappings = new ArrayList<>();
        for (Long domainId : sharedDomainIds) {
            Domain domain = domainManager.getDomain(domainId);
            if (domain == null) {
                throw new InvalidParameterValueException(String.format("Unable to find domain with ID [%s].", domainId));
            }
            try {
                accountService.checkAccess(iacTemplateOwner, domain);
            } catch (PermissionDeniedException e) {
                throw new InvalidParameterValueException(String.format("Account [%s] does not have permission to share IaC template with domain with ID [%s].", iacTemplateOwner.getAccountName(), domain.getUuid()));
            }
            IacTemplateDomainMapVO domainMapping = new IacTemplateDomainMapVO(iacTemplateId, domainId);
            iacTemplateDomainMapDao.persist(domainMapping);
            domainMappings.add(domainMapping);
        }
        return domainMappings;
    }

    private List<IacTemplateAccountMapVO> persistAccountMappings(List<Long> sharedAccountIds, List<Long> sharedProjectIds, long iacTemplateId, Account iacTemplateOwner) {
        List<IacTemplateAccountMapVO> accountMappings = new ArrayList<>();
        persistAccountMappingsForAccounts(accountMappings, sharedAccountIds, iacTemplateId, iacTemplateOwner);
        persistAccountMappingsForProjects(accountMappings, sharedProjectIds, iacTemplateId, iacTemplateOwner);
        return accountMappings;
    }

    private void persistAccountMappingsForAccounts(List<IacTemplateAccountMapVO> accountMappings, List<Long> sharedAccountIds, long iacTemplateId, Account iacTemplateOwner) {
        for (Long accountId : sharedAccountIds) {
            Account account = accountService.getActiveAccountById(accountId);
            if (account == null) {
                throw new InvalidParameterValueException(String.format("Unable to find account with ID [%s].", accountId));
            }
            try {
                accountService.checkAccess(iacTemplateOwner, null, false, account);
            } catch (PermissionDeniedException e) {
                throw new InvalidParameterValueException(String.format("Account [%s] does not have permission to share IaC template with account with ID [%s].", iacTemplateOwner.getAccountName(), account.getUuid()));
            }
            IacTemplateAccountMapVO accountMapping = new IacTemplateAccountMapVO(iacTemplateId, accountId);
            iacTemplateAccountMapDao.persist(accountMapping);
            accountMappings.add(accountMapping);
        }
    }

    private void persistAccountMappingsForProjects(List<IacTemplateAccountMapVO> accountMappings, List<Long> sharedProjectIds, long iacTemplateId, Account iacTemplateOwner) {
        for (Long projectId : sharedProjectIds) {
            Project project = projectManager.getProject(projectId);
            if (project == null) {
                throw new InvalidParameterValueException(String.format("Unable to find project with ID [%s].", projectId));
            }

            String exceptionMessage = String.format("Account [%s] does not have permission to share IaC template with project with ID [%s].", iacTemplateOwner.getAccountName(), project.getUuid());
            try {
                if (!projectManager.canAccessProjectAccount(iacTemplateOwner, project.getProjectAccountId())) {
                    throw new InvalidParameterValueException(exceptionMessage);
                }
            } catch (PermissionDeniedException e) {
                throw new InvalidParameterValueException(exceptionMessage);
            }
            IacTemplateAccountMapVO accountMapping = new IacTemplateAccountMapVO(iacTemplateId, project.getProjectAccountId());
            iacTemplateAccountMapDao.persist(accountMapping);
            accountMappings.add(accountMapping);
        }
    }

    @Override
    public void removeIacTemplate(RemoveIacTemplateCmd cmd) {
        IacTemplate iacTemplate = iacTemplateDao.findById(cmd.getId());
        if (iacTemplate == null) {
            throw new InvalidParameterValueException("Unable to find IaC template with the specified ID.");
        }

        iacTemplateDao.remove(iacTemplate.getId());
    }

    @Override
    public void deployIacTemplate(String iacTemplateContent, Map<String, String> inputs) {
        toscaOrchestrator.deployIacTemplate(iacTemplateContent, inputs);
    }

    @Override
    public IacTemplate findIacTemplateById(Long id) {
        return iacTemplateDao.findById(id);
    }

    @Override
    public void cleanUpAccountIacTemplates(long accountId) {
        iacTemplateDao.removeByAccountId(accountId);
    }

    @Override
    public void cleanUpIacTemplateDomainMappings(long domainId) {
        iacTemplateDomainMapDao.removeByDomainId(domainId);
    }

    @Override
    public List<? extends ControlledEntity> listAccountIacTemplates(long accountId) {
        return iacTemplateDao.listByAccountId(accountId);
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
        return List.of(ListIacResourceTypesCmd.class, RegisterIacTemplateCmd.class, RemoveIacTemplateCmd.class,
                UpdateIacTemplateCmd.class, DeployIacTemplateCmd.class);
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

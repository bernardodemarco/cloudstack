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
import com.cloud.domain.dao.DomainDao;
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
import org.apache.cloudstack.api.command.ListIacTemplatesCmd;
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
import java.util.Set;
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
    private DomainDao domainDao;

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

    /**
     *
     * ok, aqui temos vários casos possíveis
     * 1. Caller usuario normal
     * 1.1. nao especificou ACL params? forca para que retorne so seus templates
     * 1.2. especificou ACL params -> checagem de acesso
     *
     * SE tem acesso aos ACL params
     * 1. Pegar lista de domain ids (considerando recursividade)
     * 2. lista de contas -> tipo listconsolesessions
     *
     * SO QUE, se é para listar templates compartilhados, buscar os IDs nas tabelas auxiliares (templates compartilhados tem que ser os compartilhados com a conta definida pelos ACL params)
     * dominios compartilhados com o dominio e com a conta do ACL
     */
    @Override
    public ListResponse<IacTemplateResponse> listIacTemplates(ListIacTemplatesCmd cmd) {
        Account caller = CallContext.current().getCallingAccount();
        long domainId = getBaseDomainIdToListIacTemplatesFrom(cmd.getDomainId(), caller);
        List<Long> domainIds = cmd.isRecursive() ? domainDao.getDomainAndChildrenIds(domainId) : List.of(domainId);
        Long accountId = getAccountIdToListIacTemplatesFor(cmd.getAccountId(), cmd.getProjectId(), caller);

        List<Long> sharedIacTemplateIds = new ArrayList<>();
        // WHERE id = blabla AND name = blabla AND name LIKE %blabla%
        // AND domain_id IN (domain1, domain2, domain3) OR id IN (shareddomainid1, shareddomainid2)
        if (cmd.isShowSharedIacTemplates()) {
            sharedIacTemplateIds = getListOfSharedIacTemplatesIds(domainId, ObjectUtils.defaultIfNull(accountId, caller.getId()));
        }

        iacTemplateDao.listIacTemplates(cmd.getId(), cmd.getName(), domainIds, accountId,
                cmd.isShowIacTemplateContent(), cmd.isShowSharedIacTemplates(), cmd.getKeyword(),
                cmd.getPageSizeVal(), cmd.getStartIndex());

        return null;
    }

    List<Long> getListOfSharedIacTemplatesIds(long domainId, Long accountId) {
        List<Long> sharedIacTemplateIds = new ArrayList<>();

        iacTemplateAccountMapDao.listByAccountId(accountId).stream()
                .map(IacTemplateAccountMapVO::getIacTemplateId)
                .forEach(sharedIacTemplateIds::add);
        iacTemplateDomainMapDao.listByDomainId(domainId).stream()
                .map(IacTemplateDomainMapVO::getIacTemplateId)
                .forEach(sharedIacTemplateIds::add);
        
        return sharedIacTemplateIds;
    }

    private long getBaseDomainIdToListIacTemplatesFrom(Long domainId, Account caller) {
        if (domainId == null) {
            return caller.getDomainId();
        }

        Domain domain = domainDao.findById(domainId);
        if (domain == null) {
            throw new InvalidParameterValueException("Unable to find the specified domain.");
        }

        accountService.checkAccess(caller, domain);
        return domainId;
    }

    private Long getAccountIdToListIacTemplatesFor(Long accountId, Long projectId, Account caller) {
        if (ObjectUtils.allNotNull(accountId, projectId)) {
            throw new InvalidParameterValueException("Parameters [accountid, projectid] cannot be specified together. Please specify only one of them.");
        }

        if (projectId != null) {
            return getProjectAccountIdToListIacTemplatesFor(projectId, caller);
        }

        if (accountId != null) {
            return accountId;
        }

        if (accountService.isNormalUser(caller.getId())) {
            return caller.getId();
        }

        return null;
    }

    private long getProjectAccountIdToListIacTemplatesFor(long projectId, Account caller) {
        Project project = projectManager.getProject(projectId);
        if (project == null) {
            throw new InvalidParameterValueException("Unable to find the specified project.");
        }

        String exceptionMessage = String.format("Account [%s] does not have permission to access project with ID [%s].", caller.getAccountName(), project.getUuid());
        try {
            if (!projectManager.canAccessProjectAccount(caller, project.getProjectAccountId())) {
                throw new InvalidParameterValueException(exceptionMessage);
            }
        } catch (PermissionDeniedException e) {
            throw new InvalidParameterValueException(exceptionMessage);
        }

        return project.getProjectAccountId();
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
                persistDomainMappings(cmd.getSharedDomainIds(), persistedTemplate.getId(), owner);
            }

            if (cmd.getSharedAccountIds() != null || cmd.getSharedProjectIds() != null) {
                if (iacTemplateUpdate && cmd.getSharedAccountIds() != null) {
                    iacTemplateAccountMapDao.removeUserAccountMappingsByIacTemplateId(iacTemplate.getId());
                }
                if (iacTemplateUpdate && cmd.getSharedProjectIds() != null) {
                    iacTemplateAccountMapDao.removeProjectAccountMappingsByIacTemplateId(iacTemplate.getId());
                }
                persistAccountMappings(cmd.getSharedAccountIds(), cmd.getSharedProjectIds(), persistedTemplate.getId(), owner);
            }
            return iacTemplateDao.findById(persistedTemplate.getId());
        });
    }

    private void persistDomainMappings(Set<Long> sharedDomainIds, long iacTemplateId, Account iacTemplateOwner) {


        // alterar aqui para ja fazer o spread sobre os dominios recursivamente
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
        }
    }

    private void persistAccountMappings(Set<Long> sharedAccountIds, Set<Long> sharedProjectIds, long iacTemplateId, Account iacTemplateOwner) {
        if (sharedAccountIds != null) {
            persistAccountMappingsForAccounts(sharedAccountIds, iacTemplateId, iacTemplateOwner);
        }
        if (sharedProjectIds != null) {
            persistAccountMappingsForProjects(sharedProjectIds, iacTemplateId, iacTemplateOwner);
        }
    }

    private void persistAccountMappingsForAccounts(Set<Long> sharedAccountIds, long iacTemplateId, Account iacTemplateOwner) {
        for (Long accountId : sharedAccountIds) {
            Account account = accountService.getActiveAccountById(accountId);
            if (account == null) {
                throw new InvalidParameterValueException(String.format("Unable to find account with ID [%s].", accountId));
            }

            if (account.getId() == iacTemplateOwner.getId()) {
                throw new InvalidParameterValueException(String.format("Account [%s] cannot share IaC template with itself.", iacTemplateOwner.getAccountName()));
            }

            try {
                accountService.checkAccess(iacTemplateOwner, null, false, account);
            } catch (PermissionDeniedException e) {
                throw new InvalidParameterValueException(String.format("Account [%s] does not have permission to share IaC template with account with ID [%s].", iacTemplateOwner.getAccountName(), account.getUuid()));
            }
            IacTemplateAccountMapVO accountMapping = new IacTemplateAccountMapVO(iacTemplateId, accountId, false);
            iacTemplateAccountMapDao.persist(accountMapping);
        }
    }

    private void persistAccountMappingsForProjects(Set<Long> sharedProjectIds, long iacTemplateId, Account iacTemplateOwner) {
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
            IacTemplateAccountMapVO accountMapping = new IacTemplateAccountMapVO(iacTemplateId, project.getProjectAccountId(), true);
            iacTemplateAccountMapDao.persist(accountMapping);
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

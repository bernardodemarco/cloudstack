package org.apache.cloudstack.api.command;

import com.cloud.user.Account;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.ACL;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.IacTemplateResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.persistence.iactemplates.IacTemplate;
import org.apache.cloudstack.service.NimbleService;

import javax.inject.Inject;

@APICommand(name = "listIacTemplates", description = "Lists IaC templates.",
        responseObject = IacTemplateResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        entityType = {IacTemplate.class}, authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class ListIacTemplatesCmd extends BaseListCmd {
    @Inject
    private NimbleService nimbleService;

    @ACL
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = IacTemplateResponse.class, description = "ID of the IaC template.")
    private Long id;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "Name of the IaC template.")
    private String name;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class,
            description = "ID of the domain to list IaC templates from.")
    private Long domainId;

    @ACL
    @Parameter(name = ApiConstants.ACCOUNT_ID, type = CommandType.UUID, entityType = AccountResponse.class,
            description = "ID of the account to list IaC templates from.")
    private Long accountId;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class,
            description = "ID of the project to list IaC templates from.")
    private Long projectId;

    @Parameter(name = ApiConstants.IS_RECURSIVE, type = CommandType.BOOLEAN, description = "Whether to list IaC templates recursively across sub-domains.")
    private boolean isRecursive = false;

    @Parameter(name = ApiConstants.SHOW_IAC_TEMPLATE_CONTENT, type = CommandType.BOOLEAN, description = "Whether to return the content of the IaC templates. Defaults to false.")
    private boolean showIacTemplateContent = false;

    @Parameter(name = ApiConstants.SHOW_SHARED_IAC_TEMPLATES, type = CommandType.BOOLEAN, description = "Whether to return shared IaC templates.")
    private boolean showSharedIacTemplates = false;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getDomainId() {
        return domainId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public boolean isRecursive() {
        return isRecursive;
    }

    public boolean isShowIacTemplateContent() {
        return showIacTemplateContent;
    }

    public boolean isShowSharedIacTemplates() {
        return showSharedIacTemplates;
    }

    @Override
    public long getEntityOwnerId() {
        if (getId() != null) {
            IacTemplate iacTemplate = nimbleService.findIacTemplateById(id);
            if (iacTemplate != null) {
                return iacTemplate.getAccountId();
            }
        }

        if (getAccountId() != null) {
            return getAccountId();
        }

        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute() {
        ListResponse<IacTemplateResponse> response = nimbleService.listIacTemplates(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}

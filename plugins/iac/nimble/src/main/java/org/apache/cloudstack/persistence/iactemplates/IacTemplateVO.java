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
package org.apache.cloudstack.persistence.iactemplates;

import com.cloud.utils.db.GenericDao;
import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "iac_templates")
public class IacTemplateVO implements IacTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uuid", nullable = false)
    private String uuid = UUID.randomUUID().toString();

    @Column(name = "name", nullable = false, length = 2048)
    private String name;

    @Column(name = "description", length = 4096)
    private String description;

    @Column(name = "iac_template_content", nullable = false, length = 65535)
    private String iacTemplateContent;

    @Column(name = "recursive_domains", nullable = false)
    private boolean recursiveDomains;

    @Column(name = "domain_id", nullable = false)
    private long domainId;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(name = GenericDao.CREATED_COLUMN, nullable = false)
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date created;

    @Column(name = GenericDao.REMOVED_COLUMN)
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date removed;

    @Transient
    private List<IacTemplateAccountMapVO> accountMappings = new ArrayList<>();

    @Transient
    private List<IacTemplateDomainMapVO> domainMappings = new ArrayList<>();

    public IacTemplateVO() {
    }

    public IacTemplateVO(String name, String description, String iacTemplateContent, boolean recursiveDomains, long domainId, long accountId) {
        this.name = name;
        this.description = description;
        this.iacTemplateContent = iacTemplateContent;
        this.recursiveDomains = recursiveDomains;
        this.domainId = domainId;
        this.accountId = accountId;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getIacTemplateContent() {
        return iacTemplateContent;
    }

    @Override
    public boolean isRecursiveDomains() {
        return recursiveDomains;
    }

    @Override
    public long getDomainId() {
        return domainId;
    }

    @Override
    public long getAccountId() {
        return accountId;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public Date getRemoved() {
        return removed;
    }

    public void setAccountMappings(List<IacTemplateAccountMapVO> accountMappings) {
        this.accountMappings = accountMappings;
    }

    public List<IacTemplateAccountMapVO> getAccountMappings() {
        return accountMappings;
    }

    public void setDomainMappings(List<IacTemplateDomainMapVO> domainMappings) {
        this.domainMappings = domainMappings;
    }

    public List<IacTemplateDomainMapVO> getDomainMappings() {
        return domainMappings;
    }

    @Override
    public Class<?> getEntityType() {
        return IacTemplate.class;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilderUtils.reflectOnlySelectedFields(this, "uuid", "name");
    }
}

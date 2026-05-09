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

import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.List;

@Component
public class IacTemplateDaoImpl extends GenericDaoBase<IacTemplateVO, Long> implements IacTemplateDao {
    private static final String ACCOUNT_ID = "accountId";

    @Inject
    private IacTemplateAccountMapDao iacTemplateAccountMapDao;

    @Inject
    private IacTemplateDomainMapDao iacTemplateDomainMapDao;

    private final SearchBuilder<IacTemplateVO> iacTemplatesSearch;

    public IacTemplateDaoImpl() {
        iacTemplatesSearch = createSearchBuilder();
        iacTemplatesSearch.and(ACCOUNT_ID, iacTemplatesSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        iacTemplatesSearch.done();
    }

    @Override
    public IacTemplateVO findById(Long id) {
        IacTemplateVO iacTemplate = super.findById(id);
        if (iacTemplate == null) {
            return null;
        }

        List<IacTemplateAccountMapVO> accountMappings = iacTemplateAccountMapDao.listByIacTemplateId(iacTemplate.getId());
        iacTemplate.setAccountMappings(accountMappings);
        List<IacTemplateDomainMapVO> domainMappings = iacTemplateDomainMapDao.listByIacTemplateId(iacTemplate.getId());
        iacTemplate.setDomainMappings(domainMappings);
        return iacTemplate;
    }

    @Override
    @DB
    public boolean remove(Long id) {
        iacTemplateAccountMapDao.removeByIacTemplateId(id);
        iacTemplateDomainMapDao.removeByIacTemplateId(id);
        return super.remove(id);
    }

    @Override
    @DB
    public void removeByAccountId(long id) {
        iacTemplateAccountMapDao.removeByAccountId(id);
        SearchCriteria<IacTemplateVO> searchCriteria = iacTemplatesSearch.create();
        searchCriteria.setParameters(ACCOUNT_ID, id);
        remove(searchCriteria);
    }

    @Override
    public List<IacTemplateVO> listByAccountId(long accountId) {
        SearchCriteria<IacTemplateVO> searchCriteria = iacTemplatesSearch.create();
        searchCriteria.setParameters(ACCOUNT_ID, accountId);
        return listBy(searchCriteria);
    }
}

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

import com.cloud.utils.Pair;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.List;

@Component
public class IacTemplateDaoImpl extends GenericDaoBase<IacTemplateVO, Long> implements IacTemplateDao {
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String DOMAIN_IDS = "domainIds";
    private static final String ACCOUNT_ID = "accountId";
    private static final String CREATED = "created";
    private static final String NAME_LIKE_KEYWORD = "nameLikeKeyword";

    @Inject
    private IacTemplateAccountMapDao iacTemplateAccountMapDao;

    @Inject
    private IacTemplateDomainMapDao iacTemplateDomainMapDao;

    private final SearchBuilder<IacTemplateVO> iacTemplatesSearch;

    public IacTemplateDaoImpl() {
        iacTemplatesSearch = createSearchBuilder();
        iacTemplatesSearch.and(ID, iacTemplatesSearch.entity().getId(), SearchCriteria.Op.EQ);
        iacTemplatesSearch.and(NAME, iacTemplatesSearch.entity().getName(), SearchCriteria.Op.EQ);
        iacTemplatesSearch.and(DOMAIN_IDS, iacTemplatesSearch.entity().getDomainId(), SearchCriteria.Op.IN);
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

    @Override
    public Pair<List<IacTemplateVO>, Integer> listIacTemplates(Long id, String name, List<Long> domainIds, Long accountId,
                                                               boolean showIacTemplateContent, boolean showSharedIacTemplates,
                                                               String keyword, Long pageSizeVal, Long startIndex) {
        SearchCriteria<IacTemplateVO> searchCriteria = iacTemplatesSearch.create();
        searchCriteria.setParametersIfNotNull(ID, id);
        searchCriteria.setParametersIfNotNull(NAME, name);
        searchCriteria.setParameters(DOMAIN_IDS, domainIds.toArray());
        searchCriteria.setParametersIfNotNull(ACCOUNT_ID, accountId);
        if (keyword != null) {
            searchCriteria.setParameters(NAME_LIKE_KEYWORD, "%" + keyword + "%");
        }

        Filter filter = new Filter(IacTemplateVO.class, CREATED, false, startIndex, pageSizeVal);
        return null;
    }
}

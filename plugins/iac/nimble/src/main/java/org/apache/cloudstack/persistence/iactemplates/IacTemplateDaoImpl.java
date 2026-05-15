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
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class IacTemplateDaoImpl extends GenericDaoBase<IacTemplateVO, Long> implements IacTemplateDao {
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String NAME_LIKE_KEYWORD = "nameLikeKeyword";
    private static final String SHARED_IAC_TEMPLATE_IDS = "sharedIacTemplateIds";
    private static final String DOMAIN_IDS = "domainIds";
    private static final String ACCOUNT_ID = "accountId";
    private static final String CREATED = "created";

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

    @Override
    public Pair<List<IacTemplateVO>, Integer> listIacTemplates(Long id, String name, List<Long> domainIds, Long accountId,
                                                               Set<Long> sharedIacTemplateIds, String keyword, Long pageSizeVal, Long startIndex) {
        boolean listSharedIacTemplates = CollectionUtils.isNotEmpty(sharedIacTemplateIds);
        SearchCriteria<IacTemplateVO> searchCriteria = createListIacTemplatesSearchCriteria(id, name, domainIds, accountId, sharedIacTemplateIds, keyword, listSharedIacTemplates);
        Filter filter = new Filter(IacTemplateVO.class, CREATED, false, startIndex, pageSizeVal);
        Pair<List<IacTemplateVO>, Integer> iacTemplates = searchAndCount(searchCriteria, filter);
        populateSharedEntitiesMappings(iacTemplates.first());
        return iacTemplates;
    }

    private void populateSharedEntitiesMappings(List<IacTemplateVO> iacTemplates) {
        if (CollectionUtils.isEmpty(iacTemplates)) {
            return;
        }

        List<Long> iacTemplateIds = iacTemplates.stream().map(IacTemplateVO::getId).collect(Collectors.toList());
        Map<Long, List<IacTemplateAccountMapVO>> accountMappings = iacTemplateAccountMapDao.listByIacTemplateIds(iacTemplateIds)
                .stream().collect(Collectors.groupingBy(IacTemplateAccountMapVO::getIacTemplateId));
        Map<Long, List<IacTemplateDomainMapVO>> domainMappings = iacTemplateDomainMapDao.listByIacTemplateIds(iacTemplateIds)
                .stream().collect(Collectors.groupingBy(IacTemplateDomainMapVO::getIacTemplateId));

        for (IacTemplateVO iacTemplate : iacTemplates) {
            iacTemplate.setAccountMappings(accountMappings.getOrDefault(iacTemplate.getId(), new ArrayList<>()));
            iacTemplate.setDomainMappings(domainMappings.getOrDefault(iacTemplate.getId(), new ArrayList<>()));
        }
    }

    private SearchCriteria<IacTemplateVO> createListIacTemplatesSearchCriteria(Long id, String name, List<Long> domainIds, Long accountId,
                                                                               Set<Long> sharedIacTemplateIds, String keyword, boolean listSharedIacTemplates) {
        SearchCriteria<IacTemplateVO> searchCriteria = createListIacTemplatesSearchBuilder(listSharedIacTemplates).create();

        searchCriteria.setParametersIfNotNull(ID, id);
        searchCriteria.setParametersIfNotNull(NAME, name);
        if (keyword != null) {
            searchCriteria.setParameters(NAME_LIKE_KEYWORD, "%" + keyword + "%");
        }
        if (listSharedIacTemplates) {
            searchCriteria.setParameters(SHARED_IAC_TEMPLATE_IDS, sharedIacTemplateIds.toArray());
        }
        searchCriteria.setParameters(DOMAIN_IDS, domainIds.toArray());
        searchCriteria.setParametersIfNotNull(ACCOUNT_ID, accountId);

        return searchCriteria;
    }

    private SearchBuilder<IacTemplateVO> createListIacTemplatesSearchBuilder(boolean listSharedIacTemplates) {
        SearchBuilder<IacTemplateVO> searchBuilder = createSearchBuilder();

        searchBuilder.and(ID, searchBuilder.entity().getId(), SearchCriteria.Op.EQ);
        searchBuilder.and(NAME, searchBuilder.entity().getName(), SearchCriteria.Op.EQ);
        searchBuilder.and(NAME_LIKE_KEYWORD, searchBuilder.entity().getName(), SearchCriteria.Op.LIKE);
        if (listSharedIacTemplates) {
            searchBuilder.and().op(SHARED_IAC_TEMPLATE_IDS, searchBuilder.entity().getId(), SearchCriteria.Op.IN);
            searchBuilder.or().op(DOMAIN_IDS, searchBuilder.entity().getDomainId(), SearchCriteria.Op.IN);
            searchBuilder.and(ACCOUNT_ID, searchBuilder.entity().getAccountId(), SearchCriteria.Op.EQ);
            searchBuilder.cp().cp();
        } else {
            searchBuilder.and(DOMAIN_IDS, searchBuilder.entity().getDomainId(), SearchCriteria.Op.IN);
            searchBuilder.and(ACCOUNT_ID, searchBuilder.entity().getAccountId(), SearchCriteria.Op.EQ);
        }
        searchBuilder.done();

        return searchBuilder;
    }
}

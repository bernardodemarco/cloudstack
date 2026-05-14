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
import java.util.Set;

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
        /**
         * WHERE id = <id> AND name = <name> AND name LIKE %<keyword>%
         * AND (id IN (<shareddomainid1>, <shareddomainid2>) OR (domain_id IN (<domain1>, <domain2>, <domain3>) AND account_id = <account-id>)))

         ./engine/schema/src/main/java/org/apache/cloudstack/gui/theme/dao/GuiThemeDetailsDaoImpl.java:        detailsDaoSearchBuilder.and().op("firstReplace", detailsDaoSearchBuilder.entity().getValue(), SearchCriteria.Op.LIKE_REPLACE);
         ./engine/schema/src/main/java/com/cloud/gpu/dao/GpuDeviceDaoImpl.java:            sb.op("cardNameKeyword", cardSb.entity().getName(), SearchCriteria.Op.LIKE);
         grep: ./engine/service/target/engine/WEB-INF/lib/jaxb-impl-2.3.9.jar: binary file matches
         ./engine/schema/src/main/java/com/cloud/gpu/dao/GpuDeviceDaoImpl.java:            sb.op("profileNameKeyword", profileSb.entity().getName(), SearchCriteria.Op.LIKE);
         ./engine/schema/src/main/java/com/cloud/gpu/dao/GpuDeviceDaoImpl.java:            sb.op("profileDescriptionKeyword", profileSb.entity().getDescription(), SearchCriteria.Op.LIKE);
         ./engine/schema/src/main/java/com/cloud/gpu/dao/GpuCardDaoImpl.java:            sb.op("nameKeyword", sb.entity().getName(), SearchCriteria.Op.LIKE);

         */
        iacTemplatesSearch = createSearchBuilder();
        iacTemplatesSearch.and(ID, iacTemplatesSearch.entity().getId(), SearchCriteria.Op.EQ);
        iacTemplatesSearch.and(NAME, iacTemplatesSearch.entity().getName(), SearchCriteria.Op.EQ);
        iacTemplatesSearch.and(NAME_LIKE_KEYWORD, iacTemplatesSearch.entity().getName(), SearchCriteria.Op.LIKE);
        iacTemplatesSearch.and().op(SHARED_IAC_TEMPLATE_IDS, iacTemplatesSearch.entity().getId(), SearchCriteria.Op.IN);
        iacTemplatesSearch.or().op(DOMAIN_IDS, iacTemplatesSearch.entity().getDomainId(), SearchCriteria.Op.IN);
        iacTemplatesSearch.and(ACCOUNT_ID, iacTemplatesSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        iacTemplatesSearch.cp().cp();
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
        SearchCriteria<IacTemplateVO> searchCriteria = iacTemplatesSearch.create();
        searchCriteria.setParametersIfNotNull(ID, id);
        searchCriteria.setParametersIfNotNull(NAME, name);
        if (keyword != null) {
            searchCriteria.setParameters(NAME_LIKE_KEYWORD, "%" + keyword + "%");
        }
        searchCriteria.setParameters(SHARED_IAC_TEMPLATE_IDS, sharedIacTemplateIds.toArray());
        searchCriteria.setParameters(DOMAIN_IDS, domainIds.toArray());
        searchCriteria.setParametersIfNotNull(ACCOUNT_ID, accountId);

        Filter filter = new Filter(IacTemplateVO.class, CREATED, false, startIndex, pageSizeVal);
        return searchAndCount(searchCriteria, filter);
    }
}

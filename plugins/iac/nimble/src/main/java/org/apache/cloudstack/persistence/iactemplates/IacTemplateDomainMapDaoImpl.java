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

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class IacTemplateDomainMapDaoImpl extends GenericDaoBase<IacTemplateDomainMapVO, Long> implements IacTemplateDomainMapDao {
    private static final String IAC_TEMPLATE_IDS = "iacTemplateIds";
    private static final String IAC_TEMPLATE_ID = "iacTemplateId";
    private static final String DOMAIN_ID = "domainId";

    private final SearchBuilder<IacTemplateDomainMapVO> domainMappingSearch;
    private final SearchBuilder<IacTemplateDomainMapVO> domainMappingSearchByIacTemplateIds;

    public IacTemplateDomainMapDaoImpl() {
        domainMappingSearch = createSearchBuilder();
        domainMappingSearch.and(IAC_TEMPLATE_ID, domainMappingSearch.entity().getIacTemplateId(), SearchCriteria.Op.EQ);
        domainMappingSearch.and(DOMAIN_ID, domainMappingSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        domainMappingSearch.done();

        domainMappingSearchByIacTemplateIds = createSearchBuilder();
        domainMappingSearchByIacTemplateIds.and(IAC_TEMPLATE_IDS, domainMappingSearchByIacTemplateIds.entity().getIacTemplateId(), SearchCriteria.Op.IN);
        domainMappingSearchByIacTemplateIds.done();
    }

    @Override
    public List<IacTemplateDomainMapVO> listByIacTemplateId(long iacTemplateId) {
        SearchCriteria<IacTemplateDomainMapVO> searchCriteria = domainMappingSearch.create();
        searchCriteria.setParameters(IAC_TEMPLATE_ID, iacTemplateId);
        return listBy(searchCriteria);
    }

    @Override
    public List<IacTemplateDomainMapVO> listByIacTemplateIds(List<Long> iacTemplateIds) {
        if (CollectionUtils.isEmpty(iacTemplateIds)) {
            return new ArrayList<>();
        }

        SearchCriteria<IacTemplateDomainMapVO> searchCriteria = domainMappingSearchByIacTemplateIds.create();
        searchCriteria.setParameters(IAC_TEMPLATE_IDS, iacTemplateIds.toArray());
        return listBy(searchCriteria);
    }

    @Override
    public List<IacTemplateDomainMapVO> listByDomainId(long domainId) {
        SearchCriteria<IacTemplateDomainMapVO> searchCriteria = domainMappingSearch.create();
        searchCriteria.setParameters(DOMAIN_ID, domainId);
        return listBy(searchCriteria);
    }

    @Override
    public void removeByIacTemplateId(long iacTemplateId) {
        SearchCriteria<IacTemplateDomainMapVO> searchCriteria = domainMappingSearch.create();
        searchCriteria.setParameters(IAC_TEMPLATE_ID, iacTemplateId);
        remove(searchCriteria);
    }

    @Override
    public void removeByDomainId(long domainId) {
        SearchCriteria<IacTemplateDomainMapVO> searchCriteria = domainMappingSearch.create();
        searchCriteria.setParameters(DOMAIN_ID, domainId);
        remove(searchCriteria);
    }
}

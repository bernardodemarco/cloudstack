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
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IacTemplateAccountMapDaoImpl extends GenericDaoBase<IacTemplateAccountMapVO, Long> implements IacTemplateAccountMapDao {
    private static final String IAC_TEMPLATE_ID = "iacTemplateId";
    private static final String ACCOUNT_ID = "accountId";

    private final SearchBuilder<IacTemplateAccountMapVO> accountMappingSearch;

    public IacTemplateAccountMapDaoImpl() {
        accountMappingSearch = createSearchBuilder();
        accountMappingSearch.and(IAC_TEMPLATE_ID, accountMappingSearch.entity().getIacTemplateId(), SearchCriteria.Op.EQ);
        accountMappingSearch.and(ACCOUNT_ID, accountMappingSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        accountMappingSearch.done();
    }

    @Override
    public List<IacTemplateAccountMapVO> listByIacTemplateId(long iacTemplateId) {
        SearchCriteria<IacTemplateAccountMapVO> searchCriteria = accountMappingSearch.create();
        searchCriteria.setParameters(IAC_TEMPLATE_ID, iacTemplateId);
        return listBy(searchCriteria);
    }

    @Override
    public void removeByIacTemplateId(long iacTemplateId) {
        SearchCriteria<IacTemplateAccountMapVO> searchCriteria = accountMappingSearch.create();
        searchCriteria.setParameters(IAC_TEMPLATE_ID, iacTemplateId);
        remove(searchCriteria);
    }

    @Override
    public void removeByAccountId(long accountId) {
        SearchCriteria<IacTemplateAccountMapVO> searchCriteria = accountMappingSearch.create();
        searchCriteria.setParameters(ACCOUNT_ID, accountId);
        remove(searchCriteria);
    }
}

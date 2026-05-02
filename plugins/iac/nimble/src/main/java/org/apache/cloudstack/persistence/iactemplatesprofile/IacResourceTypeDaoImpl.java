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
package org.apache.cloudstack.persistence.iactemplatesprofile;

import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IacResourceTypeDaoImpl extends GenericDaoBase<IacResourceTypeVO, Long> implements IacResourceTypeDao {
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String CATEGORY = "category";
    private static final String NAME_LIKE_KEYWORD = "nameLikeKeyword";

    private final SearchBuilder<IacResourceTypeVO> listIacResourceTypesSearchBuilder;

    public IacResourceTypeDaoImpl() {
        listIacResourceTypesSearchBuilder = createSearchBuilder();
        listIacResourceTypesSearchBuilder.and(ID, listIacResourceTypesSearchBuilder.entity().getId(), SearchCriteria.Op.EQ);
        listIacResourceTypesSearchBuilder.and(NAME, listIacResourceTypesSearchBuilder.entity().getName(), SearchCriteria.Op.EQ);
        listIacResourceTypesSearchBuilder.and(CATEGORY, listIacResourceTypesSearchBuilder.entity().getCategory(), SearchCriteria.Op.EQ);
        listIacResourceTypesSearchBuilder.and(NAME_LIKE_KEYWORD, listIacResourceTypesSearchBuilder.entity().getName(), SearchCriteria.Op.LIKE);
        listIacResourceTypesSearchBuilder.done();
    }

    @Override
    public List<IacResourceTypeVO> listIacResourceTypes(Long id, String name, IacResourceType.Category category, String keyword, Long pageSizeVal, Long startIndex) {
        SearchCriteria<IacResourceTypeVO> searchCriteria = listIacResourceTypesSearchBuilder.create();
        searchCriteria.setParametersIfNotNull(ID, id);
        searchCriteria.setParametersIfNotNull(NAME, name);
        searchCriteria.setParametersIfNotNull(CATEGORY, category);
        if (keyword != null) {
            searchCriteria.setParameters(NAME_LIKE_KEYWORD, "%" + keyword + "%");
        }

        Filter filter = new Filter(IacResourceTypeVO.class, ID, true, startIndex, pageSizeVal);
        return search(searchCriteria, filter);
    }
}

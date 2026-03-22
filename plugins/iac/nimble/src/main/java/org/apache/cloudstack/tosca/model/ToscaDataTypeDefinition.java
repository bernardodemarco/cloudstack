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
package org.apache.cloudstack.tosca.model;

import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ToscaDataTypeDefinition {
    private final String name;
    private final Map<String, ToscaPropertyDefinition> properties;

    public ToscaDataTypeDefinition(String name, Map<String, ToscaPropertyDefinition> properties) {
        this.name = name;
        this.properties = properties;
    }

    public String getName() {
        return name;
    }

    public Map<String, ToscaPropertyDefinition> getProperties() {
        return properties;
    }

    public Set<String> getRequiredPropertyNames() {
        return properties.values().stream()
                .filter(ToscaPropertyDefinition::isRequired)
                .map(ToscaPropertyDefinition::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilderUtils.reflectOnlySelectedFields(this, "name");
    }
}

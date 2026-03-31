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

import org.apache.cloudstack.tosca.parser.ToscaYamlHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ToscaProperty {
    private final ToscaPropertyDefinition definition;
    private final Object rawValue;
    private volatile Object evaluatedValue;

    public ToscaProperty(ToscaPropertyDefinition definition, Object rawValue, Object evaluatedValue) {
        this.definition = definition;
        this.rawValue = rawValue;
        this.evaluatedValue = evaluatedValue;
    }

    public Object getRawValue() {
        return rawValue;
    }

    public Object getEvaluatedValue() {
        return evaluatedValue;
    }

    public void setEvaluatedValue(Object evaluatedValue) {
        this.evaluatedValue = evaluatedValue;
    }

    public ToscaPropertyDefinition getDefinition() {
        return definition;
    }

    protected Map<String, String> getApiRepresentationOfProperty() {
        ToscaTypeDefinition.Kind kind = definition.getType().getKind();
        if (kind == ToscaTypeDefinition.Kind.PRIMITIVE) {
            return Map.of(definition.getApiParameter(), String.valueOf(evaluatedValue));
        }

        if (kind == ToscaTypeDefinition.Kind.COLLECTION && definition.getType().getCollectionType() == ToscaCollectionType.LIST) {
            return getApiRepresentationOfLists();
        }

        if (kind == ToscaTypeDefinition.Kind.COLLECTION && definition.getType().getCollectionType() == ToscaCollectionType.MAP) {
            return getApiRepresentationOfMaps(0, ToscaYamlHelper.asMap(evaluatedValue), null);
        }

        return getApiRepresentationOfMaps(0, ToscaYamlHelper.asMap(evaluatedValue), definition.getType().getDataType());
    }

    private Map<String, String> getApiRepresentationOfLists() {
        ToscaTypeDefinition.Kind entrySchemaKind = definition.getType().getEntrySchema().getKind();
        if (entrySchemaKind == ToscaTypeDefinition.Kind.PRIMITIVE) {
            List<String> items = ToscaYamlHelper.asList(evaluatedValue).stream().map(item -> String.valueOf((Object) item)).collect(Collectors.toList());
            return Map.of(definition.getApiParameter(), String.join(",", items));
        }

        Map<String, String> params = new HashMap<>();
        List<?> items = ToscaYamlHelper.asList(evaluatedValue);
        ToscaDataTypeDefinition dataTypeDefinition = definition.getType().getEntrySchema().getDataType();
        for (int i = 0; i < items.size(); i++) {
            params.putAll(getApiRepresentationOfMaps(i, ToscaYamlHelper.asMap(items.get(i)), dataTypeDefinition));
        }
        return params;
    }

    private Map<String, String> getApiRepresentationOfMaps(int index, Map<String, Object> map, ToscaDataTypeDefinition dataTypeDefinition) {
        Map<String, String> params = new HashMap<>();
        String key = String.format("%s[%d].", definition.getApiParameter(), index);
        map.forEach((field, value) -> {
            String apiFieldName = dataTypeDefinition == null ? field : dataTypeDefinition.getProperties().get(field).getApiParameter();
            params.put(key + apiFieldName, String.valueOf(value));
        });
        return params;
    }
}

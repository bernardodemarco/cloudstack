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

import org.apache.cloudstack.tosca.parser.ToscaGetterFunctionCallContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ToscaNodeTemplate {
    private final String name;
    private final ToscaNodeType type;
    private final Map<String, ToscaProperty> properties;
    private Set<ToscaGetterFunctionCallContext> getPropertyFunctionCalls;
    private Set<ToscaGetterFunctionCallContext> getAttributeFunctionCalls;
    private final Map<String, Object> attributes = new HashMap<>();

    public ToscaNodeTemplate(String name, ToscaNodeType type, Map<String, ToscaProperty> properties) {
        this.name = name;
        this.type = type;
        this.properties = properties;
    }

    public String getName() {
        return name;
    }

    public ToscaNodeType getType() {
        return type;
    }

    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    public ToscaProperty getProperty(String name) {
        return properties.get(name);
    }

    public Map<String, ToscaProperty> getProperties() {
        return properties;
    }

    public void setGetPropertyFunctionCalls(Set<ToscaGetterFunctionCallContext> getPropertyFunctionCalls) {
        this.getPropertyFunctionCalls = getPropertyFunctionCalls;
    }

    public Set<ToscaGetterFunctionCallContext> getGetPropertyFunctionCalls() {
        return getPropertyFunctionCalls;
    }

    public void setGetAttributeFunctionCalls(Set<ToscaGetterFunctionCallContext> getAttributeFunctionCalls) {
        this.getAttributeFunctionCalls = getAttributeFunctionCalls;
    }

    public Set<ToscaGetterFunctionCallContext> getGetAttributeFunctionCalls() {
        return getAttributeFunctionCalls;
    }

    public Map<String, String> getApiParams() {
        Map<String, String> params = new HashMap<>();

        for (ToscaProperty property : properties.values()) {
            params.putAll(property.getApiRepresentationOfProperty());
        }

        return params;
    }

    public void addAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}

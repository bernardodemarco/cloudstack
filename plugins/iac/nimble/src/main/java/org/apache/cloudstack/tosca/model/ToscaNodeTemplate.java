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

import java.util.HashMap;
import java.util.Map;

public class ToscaNodeTemplate {
    private final String name;
    private final ToscaNodeType type;
    private final Map<String, ToscaProperty> properties = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();

    public ToscaNodeTemplate(String name, ToscaNodeType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void addProperty(String name, ToscaProperty property) {
        properties.put(name, property);
    }

    public void addAttribute(String name, Object value) {
        attributes.put(name, value);
    }
}

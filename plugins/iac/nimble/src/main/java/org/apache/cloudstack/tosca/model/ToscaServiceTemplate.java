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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ToscaServiceTemplate {
    private final Map<String, ToscaNodeTemplate> nodeTemplates;
    private final Map<String, ToscaInputDefinition> inputs;
    private final Map<String, Set<ToscaNodeTemplate>> dependencyGraph;
    private final Map<String, Set<ToscaProperty>> unresolvedPropertiesByGetInput;

    public ToscaServiceTemplate(Map<String, ToscaNodeTemplate> nodeTemplates, Map<String, Set<ToscaNodeTemplate>> dependencyGraph, Map<String, ToscaInputDefinition> inputs, Map<String, Set<ToscaProperty>> unresolvedPropertiesByGetInput) {
        this.nodeTemplates = nodeTemplates;
        this.dependencyGraph = dependencyGraph;
        this.inputs = inputs;
        this.unresolvedPropertiesByGetInput = unresolvedPropertiesByGetInput;
    }

    public Map<String, ToscaNodeTemplate> getNodeTemplates() {
        return Collections.unmodifiableMap(nodeTemplates);
    }

    public Map<String, ToscaInputDefinition> getInputs() {
        return Collections.unmodifiableMap(inputs);
    }

    public Map<String, Set<ToscaNodeTemplate>> getDependencyGraph() {
        return Collections.unmodifiableMap(dependencyGraph);
    }

    public Map<String, Set<ToscaProperty>> getUnresolvedPropertiesByGetInput() {
        return Collections.unmodifiableMap(unresolvedPropertiesByGetInput);
    }
}

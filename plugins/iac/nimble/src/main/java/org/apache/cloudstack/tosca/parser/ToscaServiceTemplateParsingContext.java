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
package org.apache.cloudstack.tosca.parser;

import org.apache.cloudstack.tosca.model.ToscaInputDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaProperty;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ToscaServiceTemplateParsingContext {
    private final Map<String, ToscaNodeType> profile;
    private Map<String, ToscaInputDefinition> inputs;
    private final ToscaParsingErrorsContext errorsContext = new ToscaParsingErrorsContext();
    private final Map<String, Set<ToscaGetterFunctionCallContext>> getInputFunctionCalls = new HashMap<>();
    private final Map<String, Set<ToscaGetterFunctionCallContext>> getPropertyFunctionCalls = new HashMap<>();
    private final Map<String, Set<ToscaGetterFunctionCallContext>> getAttributeFunctionCalls = new HashMap<>();
    private final Map<String, Set<String>> nodeDependencies = new HashMap<>();

    public ToscaServiceTemplateParsingContext(Map<String, ToscaNodeType> profile) {
        this.profile = profile;
    }

    public void setInputs(Map<String, ToscaInputDefinition> inputs) {
        this.inputs = inputs;
    }

    public void addGetInputFunctionCalls(String nodeTemplateName, Set<ToscaGetterFunctionCallContext> functionCallContexts) {
        getInputFunctionCalls.computeIfAbsent(nodeTemplateName, name -> new HashSet<>()).addAll(functionCallContexts);
    }

    public void addGetPropertyFunctionCalls(String nodeTemplateName, Set<ToscaGetterFunctionCallContext> functionCallContexts) {
        getPropertyFunctionCalls.computeIfAbsent(nodeTemplateName, name -> new HashSet<>()).addAll(functionCallContexts);
    }

    public void addGetAttributeFunctionCalls(String nodeTemplateName, Set<ToscaGetterFunctionCallContext> functionCallContexts) {
        getAttributeFunctionCalls.computeIfAbsent(nodeTemplateName, name -> new HashSet<>()).addAll(functionCallContexts);
    }

    public void addNodeDependency(String nodeTemplateName, String dependency) {
        nodeDependencies.computeIfAbsent(nodeTemplateName, name -> new HashSet<>()).add(dependency);
    }

    public boolean checkExistingDependency(String nodeTemplateName, String dependency) {
        return nodeDependencies.containsKey(nodeTemplateName) && nodeDependencies.get(nodeTemplateName).contains(dependency);
    }

    public Map<String, ToscaNodeType> getProfile() {
        return Collections.unmodifiableMap(profile);
    }

    public Map<String, ToscaInputDefinition> getInputs() {
        return Collections.unmodifiableMap(inputs);
    }

    public Map<String, Set<ToscaProperty>> getUnresolvedPropertiesByGetInput() {
        return getInputFunctionCalls.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(ToscaGetterFunctionCallContext::getProperty)
                                .collect(Collectors.toSet())));
    }

    public Map<String, Set<ToscaGetterFunctionCallContext>> getGetPropertyFunctionCalls() {
        return Collections.unmodifiableMap(getPropertyFunctionCalls);
    }

    public Map<String, Set<ToscaGetterFunctionCallContext>> getGetAttributeFunctionCalls() {
        return Collections.unmodifiableMap(getAttributeFunctionCalls);
    }

    public Map<String, Set<String>> getNodeDependencies() {
        return Collections.unmodifiableMap(nodeDependencies);
    }

    public boolean hasErrors() {
        return errorsContext.hasErrors();
    }

    public String buildErrorMessages() {
        return errorsContext.buildErrorMessages();
    }

    public void addError(String message, String context) {
        errorsContext.addError(message, context);
    }

    public void addErrors(List<String> messages, String context) {
        messages.forEach(message -> addError(message, context));
    }
}

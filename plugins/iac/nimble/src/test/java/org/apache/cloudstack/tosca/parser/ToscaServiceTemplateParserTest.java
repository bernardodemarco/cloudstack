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

import com.cloud.exception.InvalidParameterValueException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class ToscaServiceTemplateParserTest {
    private ToscaServiceTemplateParser toscaServiceTemplateParserSpy;

    @Mock
    private ToscaServiceTemplateParsingContext parsingContextMock;

    @Before
    public void setUp() {
        ToscaFieldParser toscaFieldParser = new ToscaFieldParser();
        toscaServiceTemplateParserSpy = Mockito.spy(new ToscaServiceTemplateParser(toscaFieldParser));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void parseServiceTemplateTestThrowExceptionWhenRequiredKeysOfTheRootSectionAreMissing() {
        String content = "{tosca_definitions_version: tosca_2_0, description: null}";
        toscaServiceTemplateParserSpy.parseServiceTemplate(content, null, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void parseServiceTemplateTestThrowExceptionWhenRequiredKeysOfTheServiceTemplateSectionAreMissing() {
        String content = "{tosca_definitions_version: tosca_2_0, description: null, service_template: {inputs: null}}";
        toscaServiceTemplateParserSpy.parseServiceTemplate(content, null, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void parseServiceTemplateTestThrowExceptionWhenUnknownKeysOfTheRootSectionArePresent() {
        String content = "{tosca_definitions_version: tosca_2_0, description: null, unknown-key: null, service_template: null}";
        toscaServiceTemplateParserSpy.parseServiceTemplate(content, null, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void parseServiceTemplateTestThrowExceptionWhenUnknownKeysOfTheServiceTemplateSectionArePresent() {
        String content = "{tosca_definitions_version: tosca_2_0, description: null, service_template: {inputs: null, node_templates: null, unknown-key: null}}";
        toscaServiceTemplateParserSpy.parseServiceTemplate(content, null, null);
    }

    @Test
    public void validateRequiredToscaKeysTestAddErrorToTheParsingContextWhenRequiredKeysAreMissing() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{not-required-key: value}"));
        Set<String> requiredKeys = Set.of("required-key-1", "required-key-2");

        toscaServiceTemplateParserSpy.validateRequiredToscaKeys(content, requiredKeys, "Template Section", parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(2)).addError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void validateRequiredToscaKeysTestNotAddErrorToTheParsingContextWhenAllRequiredKeysArePresent() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{required-key-1: value-1, required-key-2: value-2}"));
        Set<String> requiredKeys = Set.of("required-key-1", "required-key-2");

        toscaServiceTemplateParserSpy.validateRequiredToscaKeys(content, requiredKeys, "Template Section", parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(0)).addError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void validateKnownToscaKeysTestAddErrorToTheParsingContextWhenAnUnknownKeyIsPresent() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{not-required-key: value}"));
        Set<String> knownKeys = Set.of("known-key-1", "known-key-2");

        toscaServiceTemplateParserSpy.validateKnownToscaKeys(content, knownKeys, "Template Section", parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(1)).addError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void validateKnownToscaKeysTestNotAddErrorToTheParsingContextWhenAllKeysAreKnown() {
        Map<String, Object> content = ToscaYamlHelper.asMap(ToscaYamlHelper.loadYaml("{known-key-2: value}"));
        Set<String> knownKeys = Set.of("known-key-1", "known-key-2");

        toscaServiceTemplateParserSpy.validateKnownToscaKeys(content, knownKeys, "Template Section", parsingContextMock);
        Mockito.verify(parsingContextMock, Mockito.times(0)).addError(Mockito.anyString(), Mockito.anyString());
    }
}
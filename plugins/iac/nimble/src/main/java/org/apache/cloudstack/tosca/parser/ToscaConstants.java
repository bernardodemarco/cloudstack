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

import java.util.List;

public class ToscaConstants {
    public static final String ATTRIBUTES = "attributes";
    public static final String API_PARAMETER = "api-parameter";
    public static final String API_RESPONSE_ATTRIBUTE = "api-response-attribute";
    public static final String DATA_TYPES = "data_types";
    public static final String DEFAULT_VALUE = "default_value";
    public static final String DEPENDENCY = "dependency";
    public static final String DESCRIPTION = "description";
    public static final String ENTRY_SCHEMA = "entry_schema";
    public static final String INPUTS = "inputs";
    public static final String METADATA = "metadata";
    public static final String NODE_TEMPLATES = "node_templates";
    public static final String NODE_TYPES = "node_types";
    public static final String PROPERTIES = "properties";
    public static final String PROVISIONING_API = "provisioning-api";
    public static final String REQUIRED = "required";
    public static final String REQUIREMENTS = "requirements";
    public static final String ROLLBACK_API = "rollback-api";
    public static final String SERVICE_TEMPLATE = "service_template";
    public static final String TOSCA_DEFINITIONS_VERSION = "tosca_definitions_version";
    public static final String TYPE = "type";
    public static final String VALIDATION = "validation";

    public static final String FUNCTION_PREFIX = "$";
    public static final String GET_ATTRIBUTE_FUNCTION = "$get_attribute";
    public static final String GET_INPUT_FUNCTION = "$get_input";
    public static final String GET_PROPERTY_FUNCTION = "$get_property";
    public static final String VALID_VALUES_FUNCTION = "$valid_values";
    public static final List<String> GETTER_FUNCTION_KEYS = List.of(GET_ATTRIBUTE_FUNCTION, GET_INPUT_FUNCTION, GET_PROPERTY_FUNCTION, VALID_VALUES_FUNCTION);
}

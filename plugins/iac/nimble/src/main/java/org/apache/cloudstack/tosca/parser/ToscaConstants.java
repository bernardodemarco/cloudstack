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

public class ToscaConstants {
    public enum TypeOfToscaField {
        ATTRIBUTE, PROPERTY
    }

    public static final String DATA_TYPES_KEY = "data_types";
    public static final String NODE_TYPES_KEY = "node_types";

    public static final String NODE_TYPES_ATTRIBUTES_KEY = "attributes";
    public static final String PROPERTIES_KEY = "properties";
    public static final String DEPENDENCY_KEY = "dependency";
    public static final String NODE_TEMPLATES_REQUIREMENTS_KEY = "requirements";

    public static final String FIELDS_TYPE_KEY = "type";
    public static final String FIELDS_REQUIRED_KEY = "required";
    public static final String FIELDS_VALIDATION_KEY = "validation";
    public static final String FIELDS_DESCRIPTION_KEY = "description";
    public static final String FIELDS_ENTRY_SCHEMA_KEY = "entry_schema";
    public static final String FIELDS_ENTRY_DEFAULT_VALUE_KEY = "default_value";

    public static final String SERVICE_TEMPLATE_TOSCA_VERSION_KEY = "tosca_definitions_version";
    public static final String SERVICE_TEMPLATE_DESCRIPTION_KEY = "description";
    public static final String SERVICE_TEMPLATE_SERVICE_TEMPLATE_KEY = "service_template";
    public static final String SERVICE_TEMPLATE_INPUTS_KEY = "inputs";
    public static final String SERVICE_TEMPLATE_NODE_TEMPLATES_KEY = "node_templates";
}

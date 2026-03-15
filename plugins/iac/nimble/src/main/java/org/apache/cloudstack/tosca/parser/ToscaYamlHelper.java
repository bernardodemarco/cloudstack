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

import org.apache.commons.lang.BooleanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

public class ToscaYamlHelper {
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object rawObject) {
        if (!(rawObject instanceof Map)) {
            return Map.of();
        }

        return (Map<String, Object>) rawObject;
    }

    public static String asString(Object rawObject) {
        if (!(rawObject instanceof String)) {
            return null;
        }

        return (String) rawObject;
    }

    public static boolean asBoolean(Object rawObject) {
        if (rawObject instanceof Boolean) {
            return (Boolean) rawObject;
        }

        if (rawObject instanceof String) {
            return BooleanUtils.toBoolean((String) rawObject);
        }

        return false;
    }

    public static List<?> asList(Object rawObject) {
        if (!(rawObject instanceof List)) {
            return null;
        }

        return (List<?>) rawObject;
    }

    public static Object loadYaml(String yamlContent) {
        return new Yaml().load(yamlContent);
    }
}

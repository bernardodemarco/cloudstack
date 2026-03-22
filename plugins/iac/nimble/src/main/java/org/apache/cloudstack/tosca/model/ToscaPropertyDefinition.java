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

import org.apache.cloudstack.tosca.functions.ToscaFunction;

public class ToscaPropertyDefinition extends ToscaFieldDefinition {
    private final boolean required;
    private final ToscaFunction.ToscaBooleanFunction validation;

    public ToscaPropertyDefinition(String name, String description, ToscaTypeDefinition type, boolean required, ToscaFunction.ToscaBooleanFunction validation) {
        super(name, description, type);
        this.required = required;
        this.validation = validation;
    }

    /**
     * Creates an anonymous {@link ToscaPropertyDefinition} wrapping the given type, intended for
     * temporary use during collection parsing ({@link ToscaCollectionType}).
     * @param type the entry schema type to wrap.
     * @return an anonymous {@link ToscaPropertyDefinition} with no name, no description,
     *         not required, and no validation function.
     */
    public static ToscaPropertyDefinition ofAnonymous(ToscaTypeDefinition type) {
        return new ToscaPropertyDefinition(null, null, type, false, null);
    }

    public boolean isRequired() {
        return required;
    }

    public ToscaFunction.ToscaBooleanFunction getValidation() {
        return validation;
    }
}

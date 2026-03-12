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

import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;

public class ToscaTypeDefinition {
    private enum Kind {
        PRIMITIVE, COLLECTION, DATA_TYPE
    }

    private final Kind kind;
    private final ToscaPrimitiveType primitiveType;
    private final ToscaCollectionType collectionType;
    private final ToscaTypeDefinition entrySchema;
    private final ToscaDataTypeDefinition dataType;

    public static ToscaTypeDefinition ofPrimitive(ToscaPrimitiveType type) {
        return new ToscaTypeDefinition(Kind.PRIMITIVE, type, null, null, null);
    }

    public static ToscaTypeDefinition ofCollection(ToscaCollectionType collectionType, ToscaTypeDefinition entrySchema) {
        return new ToscaTypeDefinition(Kind.COLLECTION, null, collectionType, entrySchema, null);
    }

    public static ToscaTypeDefinition ofDataType(ToscaDataTypeDefinition dataType) {
        return new ToscaTypeDefinition(Kind.DATA_TYPE, null, null, null, dataType);
    }

    private ToscaTypeDefinition(Kind kind, ToscaPrimitiveType type, ToscaCollectionType collectionType, ToscaTypeDefinition entrySchema, ToscaDataTypeDefinition dataType) {
        this.primitiveType = type;
        this.collectionType = collectionType;
        this.entrySchema = entrySchema;
        this.dataType = dataType;
        this.kind = kind;
    }

    @Override
    public String toString() {
        if (kind == Kind.PRIMITIVE) {
            return ReflectionToStringBuilderUtils.reflectOnlySelectedFields(this, "kind", "primitiveType");
        }

        if (kind == Kind.COLLECTION) {
            return ReflectionToStringBuilderUtils.reflectOnlySelectedFields(this, "kind", "collectionType", "entrySchema");
        }

        return ReflectionToStringBuilderUtils.reflectOnlySelectedFields(this, "kind", "dataType");
    }
}

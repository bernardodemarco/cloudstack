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

import com.cloud.exception.InvalidParameterValueException;
import org.apache.cloudstack.tosca.parser.ToscaConstants;
import org.apache.cloudstack.tosca.parser.ToscaYamlHelper;
import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;
import org.apache.commons.lang3.BooleanUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToscaTypeDefinition {
    public enum Kind {
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

    private ToscaTypeDefinition(Kind kind, ToscaPrimitiveType primitiveType, ToscaCollectionType collectionType, ToscaTypeDefinition entrySchema, ToscaDataTypeDefinition dataType) {
        this.primitiveType = primitiveType;
        this.collectionType = collectionType;
        this.entrySchema = entrySchema;
        this.dataType = dataType;
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public ToscaPrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    public ToscaCollectionType getCollectionType() {
        return collectionType;
    }

    public ToscaTypeDefinition getEntrySchema() {
        return entrySchema;
    }

    public ToscaDataTypeDefinition getDataType() {
        return dataType;
    }

    public boolean isCompatibleWith(Object value) {
        Map<String, Object> valueAsMap = ToscaYamlHelper.asMap(value);
        boolean isFunctionCall = valueAsMap.size() == 1 && valueAsMap.keySet().iterator().next().startsWith(ToscaConstants.FUNCTION_PREFIX);
        if (isFunctionCall) {
            return true;
        };

        if (kind == Kind.PRIMITIVE) {
            return isCompatibleWithPrimitive(value);
        }

        if (kind == Kind.COLLECTION) {
            return isCompatibleWithCollection(value);
        }

        return isCompatibleWithDataType(value);
    }

    private boolean isCompatibleWithPrimitive(Object value) {
        switch (primitiveType) {
            case STRING: return value instanceof String;
            case INTEGER: return value instanceof Integer;
            case FLOAT: return value instanceof Double;
            case BOOLEAN: return value instanceof Boolean;
            default: return false;
        }
    }

    private boolean isCompatibleWithCollection(Object value) {
        switch (collectionType) {
            case LIST:
                if (!(value instanceof List)) return false;
                return ((List<?>) value).stream().allMatch(entrySchema::isCompatibleWith);
            case MAP:
                if (!(value instanceof Map)) return false;
                return ((Map<?, ?>) value).values().stream().allMatch(entrySchema::isCompatibleWith);
            default:
                return false;
        }
    }

    private boolean isCompatibleWithDataType(Object value) {
        if (!(value instanceof Map)) return false;
        Map<?, ?> valueMap = (Map<?, ?>) value;
        Set<String> dataTypesFields = dataType.getProperties().keySet();
        if (!dataTypesFields.containsAll(valueMap.keySet())) return false;

        return dataType.getProperties().values().stream().allMatch(property -> {
            if (!property.isRequired() && !valueMap.containsKey(property.getName())) return true;

            return property.getType().isCompatibleWith(valueMap.get(property.getName()));
        });
    }

    public boolean isAssignableFrom(ToscaTypeDefinition other) {
        if (kind != other.kind) return false;

        if (kind == Kind.PRIMITIVE) {
            return primitiveType == other.primitiveType;
        }

        if (kind == Kind.DATA_TYPE) {
            return dataType.getName().equals(other.dataType.getName());
        }

        if (other.entrySchema == null) return false;

        return entrySchema.isAssignableFrom(other.entrySchema);
    }

    public Object convertPrimitiveTypeFromString(String value) {
        if (value == null || primitiveType == null) {
            return null;
        }

        try {
            switch (primitiveType) {
                case STRING:  return value;
                case INTEGER: return Integer.parseInt(value);
                case FLOAT:   return Double.parseDouble(value);
                case BOOLEAN: return BooleanUtils.toBoolean(value, "true", "false");
                default: throw new InvalidParameterValueException(String.format("Unsupported primitive type: [%s].", primitiveType));
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterValueException(String.format("Value [%s] is not compatible with primitive type [%s].", value, primitiveType));
        }
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

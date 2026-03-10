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
        return ReflectionToStringBuilderUtils.reflectOnlySelectedFields(this, "type");
    }
}

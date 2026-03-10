package org.apache.cloudstack.tosca.model;

import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;

public abstract class ToscaFieldDefinition {
    private String name;
    private String description;
    private ToscaTypeDefinition type;

    public ToscaFieldDefinition(String name, String description, ToscaTypeDefinition type) {
        this.name = name;
        this.description = description;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ToscaTypeDefinition getType() {
        return type;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilderUtils.reflectOnlySelectedFields(this, "name", "type");
    }
}

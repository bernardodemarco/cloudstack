package org.apache.cloudstack.tosca.model;

public abstract class ToscaFieldDefinition {
    private String name;
    private String description;
    private ToscaPrimitiveType type;

    public ToscaFieldDefinition(String name, String description, ToscaPrimitiveType type) {
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

    public ToscaPrimitiveType getType() {
        return type;
    }
}

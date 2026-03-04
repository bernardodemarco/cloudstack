package org.apache.cloudstack.tosca.model;

public class ToscaAttributeDefinition extends ToscaFieldDefinition {
    public ToscaAttributeDefinition(String name, String description, ToscaPrimitiveType type) {
        super(name, description, type);
    }

    @Override
    public String toString() {
        return String.format("%s: %s", this.getClass().getName(), super.toString());
    }
}

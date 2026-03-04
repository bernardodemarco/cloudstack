package org.apache.cloudstack.tosca.model;

public class ToscaPropertyDefinition extends ToscaFieldDefinition {
    private boolean required;
    private Object validation;

    public ToscaPropertyDefinition(String name, String description, ToscaPrimitiveType type, boolean required, Object validation) {
        super(name, description, type);
        this.required = required;
        this.validation = validation;
    }

    public boolean isRequired() {
        return required;
    }

    public Object getValidation() {
        return validation;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", this.getClass().getName(), super.toString());
    }
}

package org.apache.cloudstack.tosca.model;

import java.util.Map;

public class ToscaNodeType {
    private String name;
    private Map<String, ToscaPropertyDefinition> properties;
    private Map<String, ToscaAttributeDefinition> attributes;

    public ToscaNodeType(String name, Map<String, ToscaPropertyDefinition> properties, Map<String, ToscaAttributeDefinition> attributes) {
        this.name = name;
        this.properties = properties;
        this.attributes = attributes;
    }

    public String getName() {
        return name;
    }

    public Map<String, ToscaPropertyDefinition> getProperties() {
        return properties;
    }

    public Map<String, ToscaAttributeDefinition> getAttributes() {
        return attributes;
    }
}

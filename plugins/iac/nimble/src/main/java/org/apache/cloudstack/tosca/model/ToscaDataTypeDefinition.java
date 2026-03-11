package org.apache.cloudstack.tosca.model;

import java.util.Map;

public class ToscaDataTypeDefinition {
    private final String name;
    private final Map<String, ToscaPropertyDefinition> properties;

    public ToscaDataTypeDefinition(String name, Map<String, ToscaPropertyDefinition> properties) {
        this.name = name;
        this.properties = properties;
    }
}

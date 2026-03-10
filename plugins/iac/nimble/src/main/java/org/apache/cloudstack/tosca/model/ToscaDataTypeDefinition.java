package org.apache.cloudstack.tosca.model;

import java.util.List;

public class ToscaDataTypeDefinition {
    private final String name;
    private final List<ToscaPropertyDefinition> properties;

    public ToscaDataTypeDefinition(String name, List<ToscaPropertyDefinition> properties) {
        this.name = name;
        this.properties = properties;
    }
}

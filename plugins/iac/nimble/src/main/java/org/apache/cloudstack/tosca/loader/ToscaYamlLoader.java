package org.apache.cloudstack.tosca.loader;

import org.apache.cloudstack.tosca.model.ToscaNodeType;

import java.nio.file.Path;
import java.util.Map;

public class ToscaYamlLoader {
    private final String NODE_TYPES_KEY = "node_types";
    private final String NODE_TYPES_PROPERTIES_KEY = "properties";
    private final String NODE_TYPES_ATTRIBUTES_KEY = "attributes";

    public ToscaNodeType loadNodeType(Path path) {
        Object rawYaml = YamlUtils.loadYaml(path);
        Map<String, Object> yamlRoot = YamlUtils.asMap(rawYaml);

        Map<String, Object> nodeTypes = YamlUtils.asMap(yamlRoot.get(NODE_TYPES_KEY));
        for (Map.Entry<String, Object> entry : nodeTypes.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> nodeTypeBody = YamlUtils.asMap(entry.getValue());
            parseNodeTypeProperties(nodeTypeBody.get(NODE_TYPES_PROPERTIES_KEY));
            parseNodeTypeAttributes(nodeTypeBody.get(NODE_TYPES_ATTRIBUTES_KEY));
            return new ToscaNodeType(name, null, null);
        }

        return null;
    }

    private void parseNodeTypeProperties(Object rawProperties) {

    }

    private void parseNodeTypeAttributes(Object rawAttributes) {}
}

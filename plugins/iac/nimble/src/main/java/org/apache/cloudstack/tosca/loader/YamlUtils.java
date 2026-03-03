package org.apache.cloudstack.tosca.loader;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class YamlUtils {
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object rawObject) {
        if (!(rawObject instanceof Map)) {
            return Map.of();
        }

        return (Map<String, Object>) rawObject;
    }

    public static Object loadYaml(Path path) {
        Yaml yaml = new Yaml();

        try (InputStream inputStream = Files.newInputStream(path)) {
            return yaml.load(inputStream);
        } catch (IOException exception) {
            return null;
        }
    }
}

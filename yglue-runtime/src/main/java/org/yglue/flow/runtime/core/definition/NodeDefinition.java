package org.yglue.flow.runtime.core.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NodeDefinition {
    private final String id;
    private final String type;
    private final Map<String, Object> config;
    private final List<NodeDefinition> children;

    public NodeDefinition(String id,
                          String type,
                          Map<String, Object> config,
                          List<NodeDefinition> children) {
        this.id = id;
        this.type = type;
        this.config = config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
        this.children = children == null ? List.of() : Collections.unmodifiableList(children);
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public List<NodeDefinition> getChildren() {
        return children;
    }

    public Object getConfigValue(String key) {
        return config.get(key);
    }
}

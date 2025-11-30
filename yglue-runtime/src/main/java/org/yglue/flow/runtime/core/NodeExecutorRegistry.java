package org.yglue.flow.runtime.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class NodeExecutorRegistry {

    private final Map<String, FlowExecutor> delegates = new LinkedHashMap<>();

    public NodeExecutorRegistry register(String type, FlowExecutor executor) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(executor, "executor");
        delegates.put(type.toLowerCase(), executor);
        return this;
    }

    public FlowExecutor get(String type) {
        FlowExecutor executor = delegates.get(type == null ? null : type.toLowerCase());
        if (executor == null) {
            throw new IllegalArgumentException("No executor registered for node type: " + type);
        }
        return executor;
    }
}

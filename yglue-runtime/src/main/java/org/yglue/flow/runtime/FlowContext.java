package org.yglue.flow.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class FlowContext {
    private final Map<String, Object> globals = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private final Deque<Map<String, Object>> nodeStack = new ArrayDeque<>();
    public static final String RETURN_KEY = "ret";

    public Map<String, Object> data() {
        return globals;
    }

    public Object get(String key) {
        return globals.get(key);
    }

    public void set(String key, Object value) {
        globals.put(key, value);
    }

    public <T> T getAs(String key, Class<T> type) {
        Object v = globals.get(key);
        if (v == null) return null;
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof Number n) {
            if (type == Integer.class || type == int.class) return type.cast(n.intValue());
            if (type == Long.class || type == long.class) return type.cast(n.longValue());
            if (type == Double.class || type == double.class) return type.cast(n.doubleValue());
        }
        if (type == String.class) return type.cast(String.valueOf(v));
        throw new IllegalArgumentException("Cannot cast " + v + " to " + type);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public <T> T getAttribute(String key, Supplier<T> supplier) {
        Object value = attributes.get(key);
        if (value == null && supplier != null) {
            value = supplier.get();
            if (value != null) {
                attributes.put(key, value);
            }
        }
        return (T) value;
    }

    public void pushNodeScope() {
        nodeStack.push(new HashMap<>());
    }

    public void popNodeScope() {
        if (!nodeStack.isEmpty()) {
            nodeStack.pop();
        }
    }

    public void setNodeValue(String key, Object value) {
        currentNodeScope().put(key, value);
    }

    public Object getNodeValue(String key) {
        return currentNodeScope().get(key);
    }

    private Map<String, Object> currentNodeScope() {
        if (nodeStack.isEmpty()) {
            nodeStack.push(new HashMap<>());
        }
        return nodeStack.peek();
    }

    public Map<String, Object> snapshot() {
        return new HashMap<>(globals);
    }

    public void restore(Map<String, Object> snapshot) {
        globals.clear();
        globals.putAll(Optional.ofNullable(snapshot).orElse(Map.of()));
    }

    public void setReturnValue(Object value) {
        set(RETURN_KEY, value);
    }

    public Object getReturnValue() {
        return get(RETURN_KEY);
    }
}

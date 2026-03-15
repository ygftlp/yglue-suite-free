package org.yglue.flow.runtime.core.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared execution context for the graph runtime.
 */
public class FlowContext {

    private final String ruleId;
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final Map<String, List<Object>> resolvedArgsByNodeId = new ConcurrentHashMap<>();
    private Object returnValue;
    private Throwable exception;

    public FlowContext() {
        this(null, (Map<String, Object>) null);
    }

    public FlowContext(String ruleId, Map<String, Object> initialInput) {
        this.ruleId = ruleId;
        if (initialInput != null) {
            this.variables.putAll(initialInput);
        }
    }

    public FlowContext(String ruleId, FlowContext source) {
        this(ruleId, source == null ? null : source.getVariables());
        if (source != null) {
            this.returnValue = source.getReturnValue();
            this.exception = source.getException();
        }
    }

    public String getRuleId() {
        return ruleId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Map<String, Object> data() {
        return variables;
    }

    public void put(String key, Object value) {
        if (value != null) {
            variables.put(key, value);
        } else {
            variables.remove(key);
        }
    }

    public void set(String key, Object value) {
        put(key, value);
    }

    public Object get(String key) {
        return variables.get(key);
    }

    public boolean containsKey(String key) {
        return variables.containsKey(key);
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public void setResolvedArgs(String nodeId, List<Object> resolvedArgs) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        if (resolvedArgs == null) {
            resolvedArgsByNodeId.remove(nodeId);
            return;
        }
        resolvedArgsByNodeId.put(nodeId, Collections.unmodifiableList(new ArrayList<>(resolvedArgs)));
    }

    public List<Object> getResolvedArgs(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        return resolvedArgsByNodeId.get(nodeId);
    }
}

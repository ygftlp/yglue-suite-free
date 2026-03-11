package org.yglue.flow.runtime.core.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared execution context for the graph runtime.
 */
public class FlowContext {

    private final String ruleId;
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
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
}

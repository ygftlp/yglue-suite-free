package org.yglue.flow.runtime.core;

import java.util.Map;

public class FlowExecutionResult {

    private final String ruleId;
    private final Map<String, Object> contextSnapshot;
    private final Object returnValue;

    public FlowExecutionResult(String ruleId,
                               Map<String, Object> contextSnapshot,
                               Object returnValue) {
        this.ruleId = ruleId;
        this.contextSnapshot = contextSnapshot;
        this.returnValue = returnValue;
    }

    public String getRuleId() {
        return ruleId;
    }

    public Map<String, Object> getContextSnapshot() {
        return contextSnapshot;
    }

    public Object getReturnValue() {
        return returnValue;
    }
}

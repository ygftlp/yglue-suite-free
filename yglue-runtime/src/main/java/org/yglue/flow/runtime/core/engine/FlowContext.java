package org.yglue.flow.runtime.core.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结构化 DAG 执行引擎的全局执行上下文
 * 替代之前的 LiteFlow Slot 机制
 */
public class FlowContext {

    private final String ruleId;

    /**
     * 全局变量存储池，用于保存前端 JSONPath 引用的 "ctx" 中的数据
     */
    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    /**
     * 整个流程的最终返回值
     */
    private Object returnValue;

    /**
     * 记录执行过程中抛出的异常
     */
    private Throwable exception;

    public FlowContext(String ruleId, Map<String, Object> initialInput) {
        this.ruleId = ruleId;
        if (initialInput != null) {
            this.variables.putAll(initialInput);
        }
    }

    public String getRuleId() {
        return ruleId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void put(String key, Object value) {
        if (value != null) {
            variables.put(key, value);
        } else {
            variables.remove(key);
        }
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

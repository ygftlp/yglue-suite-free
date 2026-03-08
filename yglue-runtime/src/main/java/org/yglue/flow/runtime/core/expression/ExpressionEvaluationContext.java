package org.yglue.flow.runtime.core.expression;

import org.yglue.flow.runtime.core.engine.FlowContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 表达式执行上下文，封装可用变量与附加数据�? */
public final class ExpressionEvaluationContext {

    private final Map<String, Object> variables;
    private final Map<String, Object> attributes;

    private ExpressionEvaluationContext(Builder builder) {
        this.variables = Collections.unmodifiableMap(new HashMap<>(builder.variables));
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    }

    public Map<String, Object> variables() {
        return variables;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    /**
     * 基于 {@link FlowContext} 构建上下文，默认会把 FlowContext �?data 映射为变量�?     */
    public static ExpressionEvaluationContext fromFlowContext(FlowContext flowContext) {
        Objects.requireNonNull(flowContext, "flowContext must not be null");
        Builder builder = new Builder();
        Map<String, Object> data = flowContext.data();
        // 创建一个新�?HashMap 副本，确�?SpEL 可以正确访问 Map 的键
        Map<String, Object> ctxMap = new HashMap<>(data);
        builder.variable("ctx", ctxMap);
        // 同时�?data 中的所有键值对都设置为顶级变量，方便直接访�?        data.forEach(builder::variable);
        builder.attribute("flowContext", flowContext);
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Object> variables = new HashMap<>();
        private final Map<String, Object> attributes = new HashMap<>();

        private Builder() {
        }

        public Builder variable(String name, Object value) {
            Objects.requireNonNull(name, "variable name must not be null");
            variables.put(name, value);
            return this;
        }

        public Builder variables(Map<String, ?> variables) {
            if (variables != null) {
                variables.forEach(this::variable);
            }
            return this;
        }

        public Builder attribute(String name, Object value) {
            Objects.requireNonNull(name, "attribute name must not be null");
            attributes.put(name, value);
            return this;
        }

        public Builder attributes(Map<String, ?> attributes) {
            if (attributes != null) {
                attributes.forEach(this::attribute);
            }
            return this;
        }

        public ExpressionEvaluationContext build() {
            return new ExpressionEvaluationContext(this);
        }
    }
}





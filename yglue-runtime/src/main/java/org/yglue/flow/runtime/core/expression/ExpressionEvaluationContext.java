package org.yglue.flow.runtime.core.expression;

import org.yglue.flow.runtime.FlowContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 表达式执行上下文，封装可用变量与附加数据。
 */
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
     * 基于 {@link FlowContext} 构建上下文，默认会把 FlowContext 的 data 映射为变量。
     */
    public static ExpressionEvaluationContext fromFlowContext(FlowContext flowContext) {
        Objects.requireNonNull(flowContext, "flowContext must not be null");
        Builder builder = new Builder();
        Map<String, Object> data = flowContext.data();
        builder.variable("ctx", data);
        data.forEach(builder::variable);
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





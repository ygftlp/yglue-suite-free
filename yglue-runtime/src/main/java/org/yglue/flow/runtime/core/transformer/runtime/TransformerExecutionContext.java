package org.yglue.flow.runtime.core.transformer.runtime;

import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.expression.ExpressionEngine;
import org.yglue.flow.runtime.core.expression.ExpressionEvaluationContext;
import org.yglue.flow.runtime.core.expression.ExpressionEvaluationException;
import org.yglue.flow.runtime.core.expression.ExpressionEngines;
import org.yglue.flow.runtime.core.param.ParamResolveBatchResult;
import org.yglue.flow.runtime.core.param.ResolveResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 运行 Transformer DSL 所需上下文�? */
public final class TransformerExecutionContext {

    private final FlowContext flowContext;
    private final ParamResolveBatchResult paramResults;
    private final Object input;
    private final Map<String, Object> variables;
    private final ExpressionEngine expressionEngine;

    private TransformerExecutionContext(Builder builder) {
        this.flowContext = builder.flowContext;
        this.paramResults = builder.paramResults;
        this.input = builder.input;
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(builder.variables));
        this.expressionEngine = builder.expressionEngine;
    }

    public FlowContext flowContext() {
        return flowContext;
    }

    public ParamResolveBatchResult paramResults() {
        return paramResults;
    }

    public Object input() {
        return input;
    }

    public Map<String, Object> variables() {
        return variables;
    }

    public ExpressionEngine expressionEngine() {
        return expressionEngine;
    }

    public ResolveResult<?> resolved(String name) {
        if (paramResults == null || name == null) {
            return null;
        }
        return paramResults.get(name);
    }

    public Map<String, Object> resolvedValues() {
        if (paramResults == null) {
            return Map.of();
        }
        return paramResults.results().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value()));
    }

    public ExpressionEvaluationContext toEvaluationContext(Map<String, Object> extraVariables) {
        ExpressionEvaluationContext.Builder builder = ExpressionEvaluationContext.builder();
        Map<String, Object> data = flowContext.data();
        builder.variables(data);
        builder.variable("ctx", data);
        builder.variable("flow", flowContext);
        builder.variable("resolved", resolvedValues());
        if (input != null) {
            builder.variable("input", input);
        }
        builder.variables(variables);
        if (extraVariables != null && !extraVariables.isEmpty()) {
            builder.variables(extraVariables);
        }
        builder.attribute("flowContext", flowContext);
        return builder.build();
    }

    public Object evaluateExpression(String expression, Map<String, Object> extraVariables) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            return expressionEngine.evaluate(expression, toEvaluationContext(extraVariables));
        } catch (ExpressionEvaluationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExpressionEvaluationException("Failed to evaluate transformer expression: " + expression, ex);
        }
    }

    public boolean evaluateCondition(String expression, Map<String, Object> extraVariables) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        return expressionEngine.evaluateBoolean(expression, toEvaluationContext(extraVariables));
    }

    public static Builder builder(FlowContext flowContext) {
        return new Builder(flowContext);
    }

    public static final class Builder {
        private final FlowContext flowContext;
        private ParamResolveBatchResult paramResults;
        private Object input;
        private final Map<String, Object> variables = new LinkedHashMap<>();
        private ExpressionEngine expressionEngine = ExpressionEngines.getDefault();

        private Builder(FlowContext flowContext) {
            this.flowContext = Objects.requireNonNull(flowContext, "flowContext must not be null");
        }

        public Builder paramResults(ParamResolveBatchResult paramResults) {
            this.paramResults = paramResults;
            return this;
        }

        public Builder input(Object input) {
            this.input = input;
            return this;
        }

        public Builder variable(String name, Object value) {
            if (name != null) {
                variables.put(name, value);
            }
            return this;
        }

        public Builder variables(Map<String, ?> vars) {
            if (vars != null) {
                vars.forEach(this::variable);
            }
            return this;
        }

        public Builder expressionEngine(ExpressionEngine expressionEngine) {
            this.expressionEngine = Objects.requireNonNull(expressionEngine, "expressionEngine must not be null");
            return this;
        }

        public TransformerExecutionContext build() {
            return new TransformerExecutionContext(this);
        }
    }
}


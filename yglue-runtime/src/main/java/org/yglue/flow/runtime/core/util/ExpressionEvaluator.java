package org.yglue.flow.runtime.core.util;

import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.expression.ExpressionEngine;
import org.yglue.flow.runtime.core.expression.ExpressionEvaluationContext;
import org.yglue.flow.runtime.core.expression.ExpressionEngines;


public final class ExpressionEvaluator {

    private ExpressionEvaluator() {
    }

    public static Object evaluate(Object rawExpression, FlowContext context) {
        if (rawExpression == null) {
            return null;
        }
        if (!(rawExpression instanceof String expression)) {
            return rawExpression;
        }
        if (!expression.startsWith("#{") || !expression.endsWith("}")) {
            return expression;
        }
        ExpressionEngine engine = ExpressionEngines.getDefault();
        ExpressionEvaluationContext evalContext = ExpressionEvaluationContext.fromFlowContext(context);
        return engine.evaluate(expression, evalContext);
    }

    public static boolean evaluateBoolean(Object expression, FlowContext context) {
        Object value = evaluate(expression, context);
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}

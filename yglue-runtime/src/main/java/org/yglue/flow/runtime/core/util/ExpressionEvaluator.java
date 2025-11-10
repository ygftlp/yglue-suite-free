package org.yglue.flow.runtime.core.util;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.yglue.flow.runtime.FlowContext;

import java.util.Map;

public final class ExpressionEvaluator {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private ExpressionEvaluator() {
    }

    public static Object evaluate(Object rawExpression, FlowContext context) {
        if (rawExpression == null) {
            return null;
        }
        if (!(rawExpression instanceof String expression)) {
            return rawExpression;
        }
        String expr = expression;
        boolean spel = expr.startsWith("#{") && expr.endsWith("}");
        if (!spel) {
            return expression;
        }
        expr = expr.substring(2, expr.length() - 1);
        StandardEvaluationContext ec = new StandardEvaluationContext();
        Map<String, Object> data = context.data();
        ec.setVariable("ctx", data);
        data.forEach(ec::setVariable);
        Expression parsed = PARSER.parseExpression(expr);
        return parsed.getValue(ec);
    }

    public static boolean evaluateBoolean(Object expression, FlowContext context) {
        Object value = evaluate(expression, context);
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}

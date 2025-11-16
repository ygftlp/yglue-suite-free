package org.yglue.flow.runtime.core.expression;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Spring 表达式语言（SpEL）的表达式引擎实现。
 */
final class SpelExpressionEngine implements ExpressionEngine {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ConcurrentHashMap<String, Expression> CACHE = new ConcurrentHashMap<>();

    @Override
    public Object evaluate(String expression, ExpressionEvaluationContext context) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        String trimmed = expression.trim();
        String spelExpression = unwrap(trimmed);
        try {
            Expression compiled = CACHE.computeIfAbsent(spelExpression, PARSER::parseExpression);
            StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
            Map<String, Object> variables = context.variables();
            if (!variables.isEmpty()) {
                variables.forEach(evaluationContext::setVariable);
            }
            evaluationContext.setVariables(variables);
            return compiled.getValue(evaluationContext);
        } catch (ParseException ex) {
            throw new ExpressionEvaluationException("Failed to parse expression: " + trimmed, ex);
        } catch (Exception ex) {
            throw new ExpressionEvaluationException("Failed to evaluate expression: " + trimmed, ex);
        }
    }

    private String unwrap(String expression) {
        if (expression.startsWith("#{") && expression.endsWith("}")) {
            return expression.substring(2, expression.length() - 1);
        }
        return expression;
    }
}





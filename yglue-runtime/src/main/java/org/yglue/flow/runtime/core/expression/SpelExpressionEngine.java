package org.yglue.flow.runtime.core.expression;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
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
            // 注册 MapAccessor 以支持通过点号访问 Map 的键（如 #ctx.total）
            evaluationContext.addPropertyAccessor(new MapAccessor());
            Map<String, Object> variables = context.variables();
            if (!variables.isEmpty()) {
                // 设置所有变量到 SpEL 上下文
                variables.forEach(evaluationContext::setVariable);
            }
            return compiled.getValue(evaluationContext);
        } catch (ParseException ex) {
            throw new ExpressionEvaluationException("Failed to parse expression: " + trimmed, ex);
        } catch (Exception ex) {
            throw new ExpressionEvaluationException("Failed to evaluate expression: " + trimmed + ", error: " + ex.getMessage(), ex);
        }
    }
    
    /**
     * Map 属性访问器，支持通过点号访问 Map 的键
     */
    private static class MapAccessor implements PropertyAccessor {
        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) {
            return target instanceof Map && ((Map<?, ?>) target).containsKey(name);
        }
        
        @Override
        public TypedValue read(EvaluationContext context, Object target, String name) {
            if (target instanceof Map) {
                Object value = ((Map<?, ?>) target).get(name);
                return new TypedValue(value);
            }
            return TypedValue.NULL;
        }
        
        @Override
        public boolean canWrite(EvaluationContext context, Object target, String name) {
            return target instanceof Map;
        }
        
        @Override
        public void write(EvaluationContext context, Object target, String name, Object value) {
            if (target instanceof Map) {
                ((Map<String, Object>) target).put(name, value);
            }
        }
        
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class<?>[]{Map.class};
        }
    }

    private String unwrap(String expression) {
        if (expression.startsWith("#{") && expression.endsWith("}")) {
            return expression.substring(2, expression.length() - 1);
        }
        return expression;
    }
}





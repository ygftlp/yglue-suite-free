package org.yglue.flow.runtime.core.expression;

/**
 * 通用表达式引擎接口，抽象不同实现（SpEL、MVEL 等）的评估能力。
 */
public interface ExpressionEngine {

    /**
     * 评估表达式并返回结果对象。
     *
     * @param expression 待执行的表达式字符串，不能为空
     * @param context    评估上下文，提供变量与函数
     * @return 评估结果
     * @throws ExpressionEvaluationException 评估失败时抛出
     */
    Object evaluate(String expression, ExpressionEvaluationContext context);

    /**
     * 评估布尔表达式，若结果不是布尔类型，会按照常规规则转换为布尔值。
     *
     * @param expression 布尔表达式
     * @param context    评估上下文
     * @return 布尔结果
     */
    default boolean evaluateBoolean(String expression, ExpressionEvaluationContext context) {
        Object value = evaluate(expression, context);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0D;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}



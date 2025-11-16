package org.yglue.flow.runtime.core.expression;

/**
 * 表达式评估异常，封装底层引擎抛出的错误。
 */
public class ExpressionEvaluationException extends RuntimeException {

    public ExpressionEvaluationException(String message) {
        super(message);
    }

    public ExpressionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}





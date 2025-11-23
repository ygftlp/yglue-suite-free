package org.yglue.flow.runtime.core.validator;

import org.yglue.flow.runtime.FlowContext;

/**
 * 校验上下文
 * <p>
 * 提供校验过程中需要的上下文信息，如流程上下文、表达式引擎等。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class ValidationContext {
    
    private final FlowContext flowContext;
    private final org.yglue.flow.runtime.core.expression.ExpressionEngine expressionEngine;
    private final String inputName;
    private final String valueType;
    
    /**
     * 构造函数
     *
     * @param flowContext 流程上下文
     * @param expressionEngine 表达式引擎
     * @param inputName 输入参数名称
     * @param valueType 值类型
     */
    public ValidationContext(FlowContext flowContext,
                             org.yglue.flow.runtime.core.expression.ExpressionEngine expressionEngine,
                             String inputName,
                             String valueType) {
        this.flowContext = flowContext;
        this.expressionEngine = expressionEngine;
        this.inputName = inputName;
        this.valueType = valueType;
    }
    
    public FlowContext getFlowContext() {
        return flowContext;
    }
    
    public org.yglue.flow.runtime.core.expression.ExpressionEngine getExpressionEngine() {
        return expressionEngine;
    }
    
    public String getInputName() {
        return inputName;
    }
    
    public String getValueType() {
        return valueType;
    }
}


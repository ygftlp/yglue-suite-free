package org.yglue.flow.runtime.core.validator;

import org.yglue.flow.runtime.core.engine.FlowContext;

/**
 * Validation-time context shared by validator implementations.
 */
public class ValidationContext {

    private final FlowContext flowContext;
    private final org.yglue.flow.runtime.core.expression.ExpressionEngine expressionEngine;
    private final String inputName;
    private final String valueType;

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
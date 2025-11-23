package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.expression.ExpressionEvaluationContext;
import org.yglue.flow.runtime.core.validator.*;

import java.util.Map;

/**
 * 表达式校验器
 * <p>
 * 使用 Groovy 表达式进行自定义校验。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class ExpressionValidator implements Validator {
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return ValidationResult.success();
        }
        
        String expression = getString(config, "expression");
        if (expression == null || expression.isBlank()) {
            return ValidationResult.success();
        }
        
        try {
            // 构建表达式评估上下文
            ExpressionEvaluationContext.Builder builder = ExpressionEvaluationContext.builder();
            
            // 从流程上下文构建基础上下文
            ExpressionEvaluationContext baseContext = ExpressionEvaluationContext.fromFlowContext(
                context.getFlowContext());
            
            // 复制基础变量
            builder.variables(baseContext.variables());
            
            // 添加 value 和 input 变量
            builder.variable("value", value);
            builder.variable("input", Map.of("name", context.getInputName(), "valueType", context.getValueType()));
            
            ExpressionEvaluationContext evalContext = builder.build();
            
            // 执行表达式
            Object result = context.getExpressionEngine().evaluate(expression, evalContext);
            
            // 检查结果
            boolean passed = false;
            if (result instanceof Boolean bool) {
                passed = bool;
            } else if (result != null) {
                passed = Boolean.parseBoolean(String.valueOf(result));
            }
            
            if (!passed) {
                String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                    ? rule.getMessage()
                    : "参数 '" + context.getInputName() + "' 校验失败";
                return ValidationResult.failure(message, getType());
            }
            
            return ValidationResult.success();
        } catch (Exception e) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 表达式校验执行失败: " + e.getMessage();
            return ValidationResult.failure(message, getType(), e);
        }
    }
    
    private String getString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value != null ? String.valueOf(value) : null;
    }
    
    @Override
    public String getType() {
        return "expression";
    }
    
    @Override
    public boolean supports(String valueType) {
        return true; // 所有类型都支持表达式校验
    }
}


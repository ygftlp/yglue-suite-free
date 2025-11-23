package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.validator.*;

/**
 * 必填校验器
 * <p>
 * 检查参数是否存在且不为 null。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class RequiredValidator implements Validator {
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        if (value == null) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 不能为空";
            return ValidationResult.failure(message, getType());
        }
        return ValidationResult.success();
    }
    
    @Override
    public String getType() {
        return "required";
    }
    
    @Override
    public boolean supports(String valueType) {
        return true; // 所有类型都支持必填校验
    }
}


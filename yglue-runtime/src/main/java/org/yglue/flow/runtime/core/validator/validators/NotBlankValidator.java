package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.validator.*;

/**
 * 非空白校验器
 * <p>
 * 检查字符串是否不为空白（去除空格后长度 > 0）。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class NotBlankValidator implements Validator {
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        if (value == null) {
            return ValidationResult.success(); // null 值由 required 校验器处理
        }
        
        if (!(value instanceof String)) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 必须是字符串类型";
            return ValidationResult.failure(message, getType());
        }
        
        String strValue = (String) value;
        if (strValue.trim().isEmpty()) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 不能为空白";
            return ValidationResult.failure(message, getType());
        }
        
        return ValidationResult.success();
    }
    
    @Override
    public String getType() {
        return "notBlank";
    }
    
    @Override
    public boolean supports(String valueType) {
        return "STRING".equals(valueType);
    }
}


package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.validator.*;

import java.util.Map;

/**
 * 类型校验器
 * <p>
 * 检查参数类型是否匹配。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class TypeValidator implements Validator {
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        if (value == null) {
            return ValidationResult.success(); // null 值由 required 校验器处理
        }
        
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return ValidationResult.success();
        }
        
        String expectedType = getString(config, "expectedType");
        String expectedTypeName = getString(config, "expectedTypeName");
        
        if (expectedType == null && expectedTypeName == null) {
            return ValidationResult.success();
        }
        
        // 检查基本类型
        if (expectedType != null) {
            String actualType = getActualType(value);
            if (!expectedType.equalsIgnoreCase(actualType)) {
                String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                    ? rule.getMessage()
                    : "参数 '" + context.getInputName() + "' 类型不匹配，期望: " + expectedType + "，实际: " + actualType;
                return ValidationResult.failure(message, getType());
            }
        }
        
        // 检查完整类型名
        if (expectedTypeName != null && !expectedTypeName.isBlank()) {
            String actualTypeName = value.getClass().getName();
            if (!expectedTypeName.equals(actualTypeName)) {
                String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                    ? rule.getMessage()
                    : "参数 '" + context.getInputName() + "' 类型不匹配，期望: " + expectedTypeName + "，实际: " + actualTypeName;
                return ValidationResult.failure(message, getType());
            }
        }
        
        return ValidationResult.success();
    }
    
    private String getActualType(Object value) {
        if (value instanceof String) {
            return "STRING";
        } else if (value instanceof Number) {
            return "NUMBER";
        } else if (value instanceof Boolean) {
            return "BOOLEAN";
        } else if (value.getClass().isArray()) {
            return "ARRAY";
        } else if (value instanceof java.util.Collection || value instanceof java.util.Map) {
            return "OBJECT";
        } else {
            return "OBJECT";
        }
    }
    
    private String getString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value != null ? String.valueOf(value) : null;
    }
    
    @Override
    public String getType() {
        return "type";
    }
    
    @Override
    public boolean supports(String valueType) {
        return true; // 所有类型都支持类型校验
    }
}


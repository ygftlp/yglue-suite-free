package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.validator.*;

import java.util.Collection;
import java.util.Map;

/**
 * 长度校验器
 * <p>
 * 检查字符串/数组/集合的长度是否在指定范围内。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class LengthValidator implements Validator {
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        if (value == null) {
            return ValidationResult.success(); // null 值由 required 校验器处理
        }
        
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return ValidationResult.success();
        }
        
        int length = getLength(value);
        if (length < 0) {
            // 不支持长度检查的类型
            return ValidationResult.success();
        }
        
        Integer minLength = getInteger(config, "minLength");
        Integer maxLength = getInteger(config, "maxLength");
        
        if (minLength != null && length < minLength) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 长度不能小于 " + minLength;
            return ValidationResult.failure(message, getType());
        }
        
        if (maxLength != null && length > maxLength) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 长度不能大于 " + maxLength;
            return ValidationResult.failure(message, getType());
        }
        
        return ValidationResult.success();
    }
    
    private int getLength(Object value) {
        if (value instanceof String str) {
            return str.length();
        } else if (value instanceof Collection<?> collection) {
            return collection.size();
        } else if (value instanceof Map<?, ?> map) {
            return map.size();
        } else if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value);
        }
        return -1;
    }
    
    private Integer getInteger(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    @Override
    public String getType() {
        return "length";
    }
    
    @Override
    public boolean supports(String valueType) {
        return "STRING".equals(valueType) || "ARRAY".equals(valueType);
    }
}


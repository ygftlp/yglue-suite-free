package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.validator.*;

import java.util.Map;

/**
 * 范围校验器
 * <p>
 * 检查数字是否在指定范围内。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class RangeValidator implements Validator {
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        if (value == null) {
            return ValidationResult.success(); // null 值由 required 校验器处理
        }
        
        if (!(value instanceof Number)) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 必须是数字类型";
            return ValidationResult.failure(message, getType());
        }
        
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return ValidationResult.success();
        }
        
        double numValue = ((Number) value).doubleValue();
        
        Double min = getDouble(config, "min");
        Double max = getDouble(config, "max");
        
        if (min != null && numValue < min) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 不能小于 " + min;
            return ValidationResult.failure(message, getType());
        }
        
        if (max != null && numValue > max) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 不能大于 " + max;
            return ValidationResult.failure(message, getType());
        }
        
        return ValidationResult.success();
    }
    
    private Double getDouble(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    @Override
    public String getType() {
        return "range";
    }
    
    @Override
    public boolean supports(String valueType) {
        return "NUMBER".equals(valueType);
    }
}


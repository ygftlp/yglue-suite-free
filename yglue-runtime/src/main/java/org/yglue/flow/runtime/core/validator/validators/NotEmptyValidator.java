package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.validator.*;

import java.util.Collection;
import java.util.Map;

/**
 * 非空校验器
 * <p>
 * 检查集合/数组/Map 是否不为空（长度 > 0）。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class NotEmptyValidator implements Validator {
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        if (value == null) {
            return ValidationResult.success(); // null 值由 required 校验器处理
        }
        
        boolean isEmpty = false;
        if (value instanceof Collection<?> collection) {
            isEmpty = collection.isEmpty();
        } else if (value instanceof Map<?, ?> map) {
            isEmpty = map.isEmpty();
        } else if (value.getClass().isArray()) {
            isEmpty = java.lang.reflect.Array.getLength(value) == 0;
        } else {
            // 非集合类型，认为不为空
            return ValidationResult.success();
        }
        
        if (isEmpty) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 不能为空";
            return ValidationResult.failure(message, getType());
        }
        
        return ValidationResult.success();
    }
    
    @Override
    public String getType() {
        return "notEmpty";
    }
    
    @Override
    public boolean supports(String valueType) {
        return "ARRAY".equals(valueType) || "OBJECT".equals(valueType);
    }
}


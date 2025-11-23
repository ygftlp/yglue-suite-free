package org.yglue.flow.runtime.core.validator;

import org.yglue.flow.runtime.core.validator.validators.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认校验引擎实现
 *
 * @author yglue
 * @since 1.0
 */
public class DefaultValidatorEngine implements ValidatorEngine {
    
    private final Map<String, Validator> validators = new HashMap<>();
    
    /**
     * 构造函数
     * <p>
     * 自动注册所有内置校验器。
     * </p>
     */
    public DefaultValidatorEngine() {
        // 注册内置校验器
        register(new RequiredValidator());
        register(new NotEmptyValidator());
        register(new NotBlankValidator());
        register(new TypeValidator());
        register(new RangeValidator());
        register(new LengthValidator());
        register(new RegexValidator());
        register(new ExpressionValidator());
        // CustomValidator 需要外部注册
    }
    
    @Override
    public List<ValidationResult> validate(Object value, List<ValidationRule> rules, ValidationContext context) {
        List<ValidationResult> results = new ArrayList<>();
        
        if (rules == null || rules.isEmpty()) {
            return results;
        }
        
        for (ValidationRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            
            Validator validator = getValidator(rule.getType());
            if (validator == null) {
                results.add(ValidationResult.failure(
                    "未知的校验器类型: " + rule.getType(), 
                    rule.getType()));
                continue;
            }
            
            ValidationResult result = validator.validate(value, rule, context);
            results.add(result);
            
            // 如果校验失败，根据 failPolicy 决定是否继续
            // 这里可以根据需要实现 failPolicy 逻辑
        }
        
        return results;
    }
    
    @Override
    public void register(Validator validator) {
        if (validator != null && validator.getType() != null) {
            validators.put(validator.getType(), validator);
        }
    }
    
    @Override
    public Validator getValidator(String type) {
        return validators.get(type);
    }
}


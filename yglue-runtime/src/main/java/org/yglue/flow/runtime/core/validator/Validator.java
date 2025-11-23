package org.yglue.flow.runtime.core.validator;

/**
 * 校验器核心接口
 * <p>
 * 所有校验器实现都应该实现此接口。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public interface Validator {
    
    /**
     * 校验值是否符合规则
     *
     * @param value 待校验的值
     * @param rule 校验规则配置
     * @param context 校验上下文
     * @return 校验结果
     */
    ValidationResult validate(Object value, ValidationRule rule, ValidationContext context);
    
    /**
     * 获取校验器类型
     *
     * @return 校验器类型（如 "required", "notEmpty", "regex" 等）
     */
    String getType();
    
    /**
     * 检查是否支持该类型的值
     *
     * @param valueType 值类型（如 "STRING", "NUMBER", "OBJECT" 等）
     * @return 如果支持则返回 true
     */
    boolean supports(String valueType);
}


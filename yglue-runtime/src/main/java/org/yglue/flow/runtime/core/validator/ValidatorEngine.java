package org.yglue.flow.runtime.core.validator;

import java.util.List;

/**
 * 校验引擎
 * <p>
 * 负责管理所有校验器，并提供统一的校验入口。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public interface ValidatorEngine {
    
    /**
     * 执行校验
     * <p>
     * 按照规则列表的顺序依次执行校验，如果某个规则失败，根据 failPolicy 决定是否继续。
     * </p>
     *
     * @param value 待校验的值
     * @param rules 校验规则列表
     * @param context 校验上下文
     * @return 校验结果列表
     */
    List<ValidationResult> validate(Object value, List<ValidationRule> rules, ValidationContext context);
    
    /**
     * 注册校验器
     *
     * @param validator 校验器实例
     */
    void register(Validator validator);
    
    /**
     * 获取校验器
     *
     * @param type 校验器类型
     * @return 校验器实例，如果不存在则返回 null
     */
    Validator getValidator(String type);
}


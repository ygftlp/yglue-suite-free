package org.yglue.flow.runtime.core.validator;

/**
 * 校验结果
 *
 * @author yglue
 * @since 1.0
 */
public class ValidationResult {
    
    private final boolean passed;
    private final String message;
    private final String validatorType;
    private final Throwable error;
    
    private ValidationResult(boolean passed, String message, String validatorType, Throwable error) {
        this.passed = passed;
        this.message = message;
        this.validatorType = validatorType;
        this.error = error;
    }
    
    /**
     * 创建成功结果
     *
     * @return 成功结果
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null, null, null);
    }
    
    /**
     * 创建失败结果
     *
     * @param message 错误消息
     * @param validatorType 校验器类型
     * @return 失败结果
     */
    public static ValidationResult failure(String message, String validatorType) {
        return new ValidationResult(false, message, validatorType, null);
    }
    
    /**
     * 创建失败结果（带异常）
     *
     * @param message 错误消息
     * @param validatorType 校验器类型
     * @param error 异常
     * @return 失败结果
     */
    public static ValidationResult failure(String message, String validatorType, Throwable error) {
        return new ValidationResult(false, message, validatorType, error);
    }
    
    public boolean isPassed() {
        return passed;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getValidatorType() {
        return validatorType;
    }
    
    public Throwable getError() {
        return error;
    }
}


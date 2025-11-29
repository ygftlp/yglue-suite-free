package org.yglue.flow.runtime.core.validator;

import java.util.List;

/**
 * 校验异常
 * <p>
 * 当参数校验失败时抛出此异常。
 * 包含错误码、错误消息和详细的校验错误信息。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class ValidationException extends RuntimeException {
    
    /** 错误码：参数校验失败 */
    public static final String ERROR_CODE_VALIDATION_FAILED = "VALIDATION_FAILED";
    
    /** 错误码：必填参数缺失 */
    public static final String ERROR_CODE_REQUIRED_MISSING = "REQUIRED_MISSING";
    
    /** 错误码：参数类型不匹配 */
    public static final String ERROR_CODE_TYPE_MISMATCH = "TYPE_MISMATCH";
    
    /** 错误码：参数格式不正确 */
    public static final String ERROR_CODE_FORMAT_INVALID = "FORMAT_INVALID";
    
    /** 错误码：参数超出范围 */
    public static final String ERROR_CODE_OUT_OF_RANGE = "OUT_OF_RANGE";
    
    /** 错误码：参数长度不符合要求 */
    public static final String ERROR_CODE_LENGTH_INVALID = "LENGTH_INVALID";
    
    /** 错误码：表达式校验失败 */
    public static final String ERROR_CODE_EXPRESSION_FAILED = "EXPRESSION_FAILED";
    
    private final String errorCode;
    private final String inputName;
    private final List<ValidationError> errors;
    
    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param inputName 输入参数名称
     */
    public ValidationException(String errorCode, String message, String inputName) {
        super(message);
        this.errorCode = errorCode;
        this.inputName = inputName;
        this.errors = List.of(new ValidationError(inputName, message, errorCode));
    }
    
    /**
     * 构造函数（带多个校验错误）
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param inputName 输入参数名称
     * @param errors 详细的校验错误列表
     */
    public ValidationException(String errorCode, String message, String inputName, List<ValidationError> errors) {
        super(message);
        this.errorCode = errorCode;
        this.inputName = inputName;
        this.errors = errors != null ? List.copyOf(errors) : List.of(new ValidationError(inputName, message, errorCode));
    }
    
    /**
     * 构造函数（带异常原因）
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param inputName 输入参数名称
     * @param cause 异常原因
     */
    public ValidationException(String errorCode, String message, String inputName, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.inputName = inputName;
        this.errors = List.of(new ValidationError(inputName, message, errorCode));
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getInputName() {
        return inputName;
    }
    
    public List<ValidationError> getErrors() {
        return errors;
    }
    
    /**
     * 转换为错误响应体（用于 REST API）
     *
     * @return 错误响应体 Map（标准格式）
     */
    public java.util.Map<String, Object> toErrorBody() {
        return toErrorBody(null);
    }
    
    /**
     * 转换为错误响应体（用于 REST API，支持自定义格式）
     *
     * @param format 错误响应格式，如果为 null 则使用标准格式
     * @return 错误响应体 Map
     */
    public java.util.Map<String, Object> toErrorBody(org.yglue.flow.runtime.core.definition.ErrorResponseFormat format) {
        if (format == null) {
            format = org.yglue.flow.runtime.core.definition.ErrorResponseFormat.STANDARD;
        }
        
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        // 使用数字错误码：失败时使用默认错误码（默认 -1）
        body.put(format.getErrorCodeField(), format.getDefaultErrorCode());
        body.put(format.getErrorMessageField(), getMessage());
        
        // 添加校验错误详情（如果有的话）
        if (format.getInputNameField() != null && !format.getInputNameField().isEmpty()) {
            body.put(format.getInputNameField(), getInputName());
        }
        
        // 注意：data 字段只在成功场景下包含业务数据，错误响应时不包含 data 字段
        // 错误信息通过 errorCode 和 message 字段已经可以表达
        
        return body;
    }
    
    /**
     * 校验错误详情
     */
    public static class ValidationError {
        private final String field;
        private final String message;
        private final String errorCode;
        
        public ValidationError(String field, String message, String errorCode) {
            this.field = field;
            this.message = message;
            this.errorCode = errorCode;
        }
        
        public String getField() {
            return field;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
    }
}


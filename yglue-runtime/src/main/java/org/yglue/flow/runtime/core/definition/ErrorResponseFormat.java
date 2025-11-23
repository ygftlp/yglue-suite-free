package org.yglue.flow.runtime.core.definition;

import java.util.Map;

/**
 * 错误响应格式配置
 * <p>
 * 用于定义不同系统的错误响应结构，支持自定义字段映射。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class ErrorResponseFormat {
    
    /** 标准格式：{errorCode, message, data} */
    public static final ErrorResponseFormat STANDARD = new ErrorResponseFormat(
        "errorCode", "message", null, "data", 1, -1
    );
    
    /** 简化格式：{code, message} */
    public static final ErrorResponseFormat SIMPLE = new ErrorResponseFormat(
        "code", "message", null, null, 1, -1
    );
    
    /** 中文格式：{错误码, 错误信息} */
    public static final ErrorResponseFormat CHINESE = new ErrorResponseFormat(
        "错误码", "错误信息", "参数名", "详情", 1, -1
    );
    
    private final String errorCodeField;
    private final String errorMessageField;
    private final String inputNameField;
    private final String detailsField;
    private final Integer successCode;
    private final Integer defaultErrorCode;
    
    /**
     * 构造函数（兼容旧版本）
     *
     * @param errorCodeField 错误码字段名
     * @param errorMessageField 错误消息字段名
     * @param inputNameField 输入参数名字段名（可选）
     * @param detailsField 详情字段名（可选）
     */
    public ErrorResponseFormat(String errorCodeField,
                               String errorMessageField,
                               String inputNameField,
                               String detailsField) {
        this(errorCodeField, errorMessageField, inputNameField, detailsField, 1, -1);
    }
    
    /**
     * 构造函数
     *
     * @param errorCodeField 错误码字段名
     * @param errorMessageField 错误消息字段名
     * @param inputNameField 输入参数名字段名（可选）
     * @param detailsField 详情字段名（可选）
     * @param successCode 成功时的错误码值（默认 1）
     * @param defaultErrorCode 失败时的默认错误码值（默认 -1）
     */
    public ErrorResponseFormat(String errorCodeField,
                               String errorMessageField,
                               String inputNameField,
                               String detailsField,
                               Integer successCode,
                               Integer defaultErrorCode) {
        this.errorCodeField = errorCodeField;
        this.errorMessageField = errorMessageField;
        this.inputNameField = inputNameField;
        this.detailsField = detailsField;
        this.successCode = successCode != null ? successCode : 1;
        this.defaultErrorCode = defaultErrorCode != null ? defaultErrorCode : -1;
    }
    
    /**
     * 从配置 Map 创建错误响应格式
     * <p>
     * 配置格式：
     * <pre>{@code
     * {
     *   "errorCodeField": "ret",
     *   "errorMessageField": "msg",
     *   "inputNameField": "field",  // 可选
     *   "detailsField": "errors"    // 可选
     * }
     * }</pre>
     * </p>
     *
     * @param config 配置 Map
     * @return 错误响应格式
     */
    public static ErrorResponseFormat fromConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return STANDARD;
        }
        
        String errorCodeField = getString(config, "errorCodeField", "errorCode");
        String errorMessageField = getString(config, "errorMessageField", "message");
        String inputNameField = getString(config, "inputNameField", null);
        String detailsField = getString(config, "detailsField", "data");
        Integer successCode = getInteger(config, "successCode", 1);
        Integer defaultErrorCode = getInteger(config, "defaultErrorCode", -1);
        
        return new ErrorResponseFormat(errorCodeField, errorMessageField, inputNameField, detailsField, successCode, defaultErrorCode);
    }
    
    private static Integer getInteger(Map<String, Object> config, String key, Integer defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 从预设名称创建错误响应格式
     *
     * @param presetName 预设名称（standard, simple, chinese）
     * @return 错误响应格式
     */
    public static ErrorResponseFormat fromPreset(String presetName) {
        if (presetName == null || presetName.isBlank()) {
            return STANDARD;
        }
        
        return switch (presetName.toLowerCase()) {
            case "standard" -> STANDARD;
            case "simple" -> SIMPLE;
            case "chinese" -> CHINESE;
            default -> STANDARD;
        };
    }
    
    private static String getString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        String str = String.valueOf(value);
        return str.isBlank() ? defaultValue : str;
    }
    
    public String getErrorCodeField() {
        return errorCodeField;
    }
    
    public String getErrorMessageField() {
        return errorMessageField;
    }
    
    public String getInputNameField() {
        return inputNameField;
    }
    
    public String getDetailsField() {
        return detailsField;
    }
    
    /**
     * 获取成功时的错误码值
     *
     * @return 成功码（默认 1）
     */
    public Integer getSuccessCode() {
        return successCode;
    }
    
    /**
     * 获取失败时的默认错误码值
     *
     * @return 默认错误码（默认 -1）
     */
    public Integer getDefaultErrorCode() {
        return defaultErrorCode;
    }
}


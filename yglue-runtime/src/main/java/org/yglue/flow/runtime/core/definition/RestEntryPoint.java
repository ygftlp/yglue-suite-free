package org.yglue.flow.runtime.core.definition;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST 入口点定义
 * 用于定义 REST 接口与流程的映射关系
 */
@Data
public class RestEntryPoint {
    private Long id;
    private String path;
    private String method;
    private String flowCode;
    private Boolean enabled;

    private JsonNode requestSchema;

    @com.fasterxml.jackson.annotation.JsonAlias("request_schema_json")
    private String requestSchemaJson;

    @com.fasterxml.jackson.annotation.JsonAlias("inbound_interceptors_json")
    private Object inboundInterceptors;
    
    /**
     * 数据响应格式配置
     * <p>
     * 支持两种配置方式：
     * 1. 预设名称：如 "standard"
     * 2. 自定义配置：如 {"errorCodeField": "ret", "errorMessageField": "msg", "customFields": [...]}
     * </p>
     */
    private Object dataResponseFormat;
    
    /**
     * 获取数据响应格式
     *
     * @return 数据响应格式
     */
    public ErrorResponseFormat getErrorResponseFormat() {
        if (dataResponseFormat == null) {
            return ErrorResponseFormat.STANDARD;
        }
        
        if (dataResponseFormat instanceof String) {
            // 预设名称
            return ErrorResponseFormat.fromPreset((String) dataResponseFormat);
        } else if (dataResponseFormat instanceof Map) {
            // 自定义配置
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) dataResponseFormat;
            return ErrorResponseFormat.fromConfig(config);
        }
        
        return ErrorResponseFormat.STANDARD;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> inboundInterceptorsView() {
        if (inboundInterceptors instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> converted = new java.util.LinkedHashMap<>();
                    map.forEach((k, v) -> converted.put(String.valueOf(k), v));
                    result.add(converted);
                }
            }
            return result;
        }
        return List.of();
    }
}

package org.yglue.flow.runtime.core.definition;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

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
    private Boolean replaceResponse;
    private Boolean enabled;

    private JsonNode requestSchema;

    @com.fasterxml.jackson.annotation.JsonAlias("request_schema_json")
    private String requestSchemaJson;
}

package org.yglue.flow.runtime.core.definition;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
}

package org.yglue.flow.orch.web.dto.endpoint.response;

import lombok.Data;

import java.util.Date;

@Data
public class ProjectComponentItemResponse {
    private Long id;
    private String name;
    private String description;
    private String endpointType;
    private String componentType;
    private String method;
    private String path;
    private String configJson;
    private Date createTime;
    private Date updateTime;
    private Long entrypointId;
    private String flowCode;
    private Boolean enabled;
    private Boolean replaceResponse;
}

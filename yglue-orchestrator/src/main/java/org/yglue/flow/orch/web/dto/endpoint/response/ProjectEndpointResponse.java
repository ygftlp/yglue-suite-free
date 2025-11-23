package org.yglue.flow.orch.web.dto.endpoint.response;

import lombok.Data;
import org.yglue.flow.orch.domain.ProjectEndpoint;

import java.util.Date;
import java.util.Map;

@Data
public class ProjectEndpointResponse {
    private Long id;
    private String endpointType;
    private String componentType;
    private String method;
    private String path;
    private String name;
    private String description;
    private String configJson;
    private Date createTime;
    private Date updateTime;
    private Long entrypointId;
    private String flowCode;
    private Boolean enabled;
    private String requestSchemaJson;
    private Map<String, Object> responseSchema;

    public static ProjectEndpointResponse from(ProjectEndpoint endpoint) {
        ProjectEndpointResponse response = new ProjectEndpointResponse();
        response.setId(endpoint.getId());
        response.setEndpointType(endpoint.getEndpointType());
        response.setComponentType(endpoint.getComponentType());
        response.setMethod(endpoint.getMethod());
        response.setPath(endpoint.getPath());
        response.setName(endpoint.getName());
        response.setDescription(endpoint.getDescription());
        response.setConfigJson(endpoint.getConfigJson());
        response.setCreateTime(endpoint.getCreateTime());
        response.setUpdateTime(endpoint.getUpdateTime());
        return response;
    }
}

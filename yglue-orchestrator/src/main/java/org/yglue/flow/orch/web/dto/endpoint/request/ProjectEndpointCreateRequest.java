package org.yglue.flow.orch.web.dto.endpoint.request;

import jakarta.validation.constraints.NotBlank;

public class ProjectEndpointCreateRequest {
    public String endpointType;

    public String componentType;

    public String method;

    public String path;

    @NotBlank
    public String name;

    public String description;

    public String configJson;

    public String createdBy;
}

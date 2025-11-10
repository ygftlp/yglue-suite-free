package org.yglue.flow.orch.web.dto.project.request;

import jakarta.validation.constraints.NotBlank;

public class ProjectCreateRequest {
    @NotBlank
    public String key;

    @NotBlank
    public String name;
}

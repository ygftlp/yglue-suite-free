package org.yglue.flow.orch.web.dto.flow.request;

import jakarta.validation.constraints.NotBlank;

public class FlowSaveRequest {
    @NotBlank
    public String code;

    @NotBlank
    public String name;

    @NotBlank
    public String contentJson; // DSL/JSON content as string

    public String createdBy;
}

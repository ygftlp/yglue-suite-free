package org.yglue.flow.orch.web.dto.stat.request;

import jakarta.validation.constraints.NotBlank;

public class StatEventRequest {
    @NotBlank
    public String eventType;

    public String flowCode;

    public String source;

    public String payloadJson;
}

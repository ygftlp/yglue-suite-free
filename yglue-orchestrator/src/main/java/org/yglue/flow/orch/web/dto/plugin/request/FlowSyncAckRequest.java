package org.yglue.flow.orch.web.dto.plugin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class FlowSyncAckRequest {
    @NotBlank
    public String flowCode;

    @NotNull
    public Integer versionNo;
}

package org.yglue.flow.orch.web.dto.flow.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlowEntryPointToggleRequest {

    @NotNull
    private Boolean enabled;

    private String updatedBy;

}

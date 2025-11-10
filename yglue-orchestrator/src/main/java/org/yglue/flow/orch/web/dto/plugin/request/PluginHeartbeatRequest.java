package org.yglue.flow.orch.web.dto.plugin.request;

import jakarta.validation.constraints.NotBlank;

public class PluginHeartbeatRequest {
    @NotBlank
    public String instanceKey;

    public String ideType;

    public String ideVersion;

    public String status;

    public String hostIp;

    public String extraInfo;
}

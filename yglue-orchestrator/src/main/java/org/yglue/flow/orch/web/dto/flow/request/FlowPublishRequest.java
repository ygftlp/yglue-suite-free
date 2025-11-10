package org.yglue.flow.orch.web.dto.flow.request;

import jakarta.validation.constraints.NotNull;

public class FlowPublishRequest {
    @NotNull
    public Integer versionNo;

    public String publishedBy;

    public String comment;

    /**
     * 入口配置。
     */
    public FlowEntryPointRequest entrypoint;
}

package org.yglue.flow.orch.web.dto.flow.request;

import jakarta.validation.constraints.NotBlank;

public class FlowEntryPointRequest {

    @NotBlank
    private String path;

    private String method;

    private String requestSchema;

    private Boolean replaceResponse;
    private Boolean enabled;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRequestSchema() {
        return requestSchema;
    }

    public void setRequestSchema(String requestSchema) {
        this.requestSchema = requestSchema;
    }

    public Boolean getReplaceResponse() {
        return replaceResponse;
    }

    public void setReplaceResponse(Boolean replaceResponse) {
        this.replaceResponse = replaceResponse;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

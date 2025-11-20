package org.yglue.flow.orch.web.dto.flow.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class FlowEntryPointRequest {

    @NotBlank
    private String path;

    private String method;

    private String requestSchemaJson;

    private Boolean replaceResponse;
    private Boolean enabled;

    public void setPath(String path) {
        this.path = path;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setRequestSchemaJson(String requestSchemaJson) {
        this.requestSchemaJson = requestSchemaJson;
    }

    /**
     * @deprecated use {@link #getRequestSchemaJson()} instead.
     */
    @Deprecated
    public String getRequestSchema() {
        return requestSchemaJson;
    }

    /**
     * @deprecated use {@link #setRequestSchemaJson(String)} instead.
     */
    @Deprecated
    public void setRequestSchema(String requestSchema) {
        this.requestSchemaJson = requestSchema;
    }

    public void setReplaceResponse(Boolean replaceResponse) {
        this.replaceResponse = replaceResponse;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

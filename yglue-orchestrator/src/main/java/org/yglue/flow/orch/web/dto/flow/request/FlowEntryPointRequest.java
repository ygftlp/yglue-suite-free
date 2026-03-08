package org.yglue.flow.orch.web.dto.flow.request;

import jakarta.validation.constraints.NotBlank;

public class FlowEntryPointRequest {

    @NotBlank
    private String path;

    private String method;

    private String requestSchemaJson;

    private Object dataResponseFormat;

    private Object inboundInterceptors;

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

    public String getRequestSchemaJson() {
        return requestSchemaJson;
    }

    public void setRequestSchemaJson(String requestSchemaJson) {
        this.requestSchemaJson = requestSchemaJson;
    }

    public Object getDataResponseFormat() {
        return dataResponseFormat;
    }

    public void setDataResponseFormat(Object dataResponseFormat) {
        this.dataResponseFormat = dataResponseFormat;
    }

    public Object getInboundInterceptors() {
        return inboundInterceptors;
    }

    public void setInboundInterceptors(Object inboundInterceptors) {
        this.inboundInterceptors = inboundInterceptors;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

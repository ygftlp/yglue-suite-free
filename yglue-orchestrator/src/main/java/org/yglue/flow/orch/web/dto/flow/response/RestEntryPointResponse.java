package org.yglue.flow.orch.web.dto.flow.response;

public class RestEntryPointResponse {
    private Long id;
    private String path;
    private String method;
    private String flowCode;
    private String requestSchemaJson;
    private Object dataResponseFormat;
    private Object inboundInterceptors;
    private boolean enabled;

    public RestEntryPointResponse() {
    }

    public RestEntryPointResponse(Long id,
                                  String path,
                                  String method,
                                  String flowCode,
                                  String requestSchemaJson,
                                  Object dataResponseFormat,
                                  Object inboundInterceptors,
                                  boolean enabled) {
        this.id = id;
        this.path = path;
        this.method = method;
        this.flowCode = flowCode;
        this.requestSchemaJson = requestSchemaJson;
        this.dataResponseFormat = dataResponseFormat;
        this.inboundInterceptors = inboundInterceptors;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getFlowCode() {
        return flowCode;
    }

    public void setFlowCode(String flowCode) {
        this.flowCode = flowCode;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

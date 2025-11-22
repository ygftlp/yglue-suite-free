package org.yglue.flow.orch.web.dto.flow.response;

public class RestEntryPointResponse {
    private Long id;
    private String path;
    private String method;
    private String flowCode;
    private String requestSchemaJson;
    private boolean replaceResponse;
    private boolean enabled;

    public RestEntryPointResponse() {
    }

    public RestEntryPointResponse(Long id,
                                  String path,
                                  String method,
                                  String flowCode,
                                  String requestSchemaJson,
                                  boolean replaceResponse,
                                  boolean enabled) {
        this.id = id;
        this.path = path;
        this.method = method;
        this.flowCode = flowCode;
        this.requestSchemaJson = requestSchemaJson;
        this.replaceResponse = replaceResponse;
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

    public boolean isReplaceResponse() {
        return replaceResponse;
    }

    public void setReplaceResponse(boolean replaceResponse) {
        this.replaceResponse = replaceResponse;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

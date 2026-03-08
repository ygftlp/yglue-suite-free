package org.yglue.flow.orch.domain;

import java.time.Instant;

public class FlowEntryPoint {
    private Long id;
    private Long projectId;
    private String flowCode;
    private String httpMethod;
    private String path;
    private String requestSchemaJson;
    private String dataResponseFormat;
    private String inboundInterceptorsJson;
    private Boolean enabled;
    private Instant createTime;
    private Instant updateTime;
    private String createBy;
    private String updateBy;
    private Integer delFlag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getFlowCode() {
        return flowCode;
    }

    public void setFlowCode(String flowCode) {
        this.flowCode = flowCode;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRequestSchemaJson() {
        return requestSchemaJson;
    }

    public void setRequestSchemaJson(String requestSchemaJson) {
        this.requestSchemaJson = requestSchemaJson;
    }

    public String getDataResponseFormat() {
        return dataResponseFormat;
    }

    public void setDataResponseFormat(String dataResponseFormat) {
        this.dataResponseFormat = dataResponseFormat;
    }

    public String getInboundInterceptorsJson() {
        return inboundInterceptorsJson;
    }

    public void setInboundInterceptorsJson(String inboundInterceptorsJson) {
        this.inboundInterceptorsJson = inboundInterceptorsJson;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}

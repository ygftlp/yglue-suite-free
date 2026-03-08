package org.yglue.flow.orch.web.dto.flow.response;

import java.util.List;

/**
 * 流程服务签名问题响应
 */
public class FlowServiceSignatureIssueResponse {

    private String flowCode;
    private String flowName;
    private Long versionId;
    private Integer versionNo;
    private Integer issueCount;
    private List<String> issueSamples;

    public String getFlowCode() {
        return flowCode;
    }

    public void setFlowCode(String flowCode) {
        this.flowCode = flowCode;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public Integer getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(Integer issueCount) {
        this.issueCount = issueCount;
    }

    public List<String> getIssueSamples() {
        return issueSamples;
    }

    public void setIssueSamples(List<String> issueSamples) {
        this.issueSamples = issueSamples;
    }
}


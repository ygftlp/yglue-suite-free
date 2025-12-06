package org.yglue.flow.orch.domain.code;

import java.time.Instant;

public class ProjectCodeSnapshot {
    private Long id;
    private Long projectId;
    private String snapshotKey;
    private String commitHash;
    private Instant generatedAt;
    private Integer status;
    private String contentHash;
    private String ideProduct;
    private String ideVersion;
    private String ideBuild;
    private String remark;
    private Instant createdAt;
    private Instant updatedAt;

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

    public String getSnapshotKey() {
        return snapshotKey;
    }

    public void setSnapshotKey(String snapshotKey) {
        this.snapshotKey = snapshotKey;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getIdeProduct() {
        return ideProduct;
    }

    public void setIdeProduct(String ideProduct) {
        this.ideProduct = ideProduct;
    }

    public String getIdeVersion() {
        return ideVersion;
    }

    public void setIdeVersion(String ideVersion) {
        this.ideVersion = ideVersion;
    }

    public String getIdeBuild() {
        return ideBuild;
    }

    public void setIdeBuild(String ideBuild) {
        this.ideBuild = ideBuild;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

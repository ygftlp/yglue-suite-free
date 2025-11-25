package org.yglue.flow.orch.web.dto.snapshot.response;

public class CodeSnapshotUploadResponse {
    private Long snapshotId;
    private Long projectId;
    private boolean projectCreated;

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public boolean isProjectCreated() {
        return projectCreated;
    }

    public void setProjectCreated(boolean projectCreated) {
        this.projectCreated = projectCreated;
    }
}

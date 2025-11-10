package org.yglue.flow.orch.web.dto.project.response;

import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.service.ProjectService;

public class ProjectEnsureResponse {

    private boolean created;
    private Long projectId;
    private String projectKey;
    private String projectName;

    public static ProjectEnsureResponse from(ProjectService.EnsureResult result) {
        ProjectEnsureResponse resp = new ProjectEnsureResponse();
        resp.setCreated(result.created());
        Project project = result.project();
        resp.setProjectId(project.getId());
        resp.setProjectKey(project.getKey());
        resp.setProjectName(project.getName());
        return resp;
    }

    public boolean isCreated() {
        return created;
    }

    public void setCreated(boolean created) {
        this.created = created;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
}

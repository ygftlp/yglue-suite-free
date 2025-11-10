package org.yglue.flow.orch.web.dto.metadata.request;

import jakarta.validation.constraints.NotBlank;

public class MetadataUploadRequest {
    @NotBlank
    private String contentJson;
    private String projectName;

    public String getContentJson() {
        return contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
}

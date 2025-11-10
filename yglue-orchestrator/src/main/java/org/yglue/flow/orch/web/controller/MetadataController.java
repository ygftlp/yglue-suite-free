package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.ProjectMetadata;
import org.yglue.flow.orch.service.MetadataService;
import org.yglue.flow.orch.web.dto.metadata.request.MetadataUploadRequest;
import org.yglue.flow.orch.web.dto.metadata.response.MetadataUploadResponse;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectKey}/metadata")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @PostMapping
    public MetadataUploadResponse upload(@PathVariable("projectKey") String projectKey,
                                         @RequestBody @Valid MetadataUploadRequest req) {
        MetadataService.SaveResult result = metadataService.save(projectKey,
                req.getProjectName() != null && !req.getProjectName().isBlank() ? req.getProjectName() : projectKey,
                req.getContentJson());
        MetadataUploadResponse response = new MetadataUploadResponse();
        ProjectMetadata metadata = result.metadata();
        response.setMetadataId(metadata.getId());
        response.setProjectId(metadata.getProjectId());
        response.setProjectCreated(result.projectCreated());
        return response;
    }

    @GetMapping
    public List<ProjectMetadata> list(@PathVariable("projectKey") String projectKey) {
        return metadataService.list(projectKey);
    }
}

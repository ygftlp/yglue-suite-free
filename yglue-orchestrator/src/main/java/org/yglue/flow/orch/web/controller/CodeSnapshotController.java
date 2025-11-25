package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.snapshot.ProjectCodeSnapshot;
import org.yglue.flow.orch.service.CodeSnapshotService;
import org.yglue.flow.orch.service.CodeSnapshotService.SaveResult;
import org.yglue.flow.orch.web.dto.snapshot.request.CodeSnapshotUploadRequest;
import org.yglue.flow.orch.web.dto.snapshot.response.CodeSnapshotUploadResponse;

@RestController
@RequestMapping("/api/projects/{projectKey}/code-snapshots")
public class CodeSnapshotController {

    private final CodeSnapshotService codeSnapshotService;

    public CodeSnapshotController(CodeSnapshotService codeSnapshotService) {
        this.codeSnapshotService = codeSnapshotService;
    }

    @PostMapping
    public CodeSnapshotUploadResponse upload(@PathVariable("projectKey") String projectKey,
                                             @RequestBody @Valid CodeSnapshotUploadRequest request) {
        SaveResult result = codeSnapshotService.save(projectKey, projectKey, request);
        ProjectCodeSnapshot snapshot = result.snapshot();
        CodeSnapshotUploadResponse response = new CodeSnapshotUploadResponse();
        response.setSnapshotId(snapshot.getId());
        response.setProjectId(snapshot.getProjectId());
        response.setProjectCreated(result.projectCreated());
        return response;
    }
}

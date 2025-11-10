package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.service.ProjectService;
import org.yglue.flow.orch.web.dto.project.request.ProjectCreateRequest;
import org.yglue.flow.orch.web.dto.project.request.ProjectEnsureRequest;
import org.yglue.flow.orch.web.dto.project.response.ProjectEnsureResponse;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public Project create(@RequestBody @Valid ProjectCreateRequest req) {
        return projectService.create(req.key, req.name);
    }

    @PostMapping("/{projectKey}/ensure")
    public ProjectEnsureResponse ensure(@PathVariable("projectKey") String projectKey,
                                        @RequestBody(required = false) ProjectEnsureRequest req) {
        String name = req != null && req.getName() != null && !req.getName().isBlank()
                ? req.getName()
                : projectKey;
        return ProjectEnsureResponse.from(projectService.ensureProjectWithFlag(projectKey, name));
    }

    @GetMapping
    public List<Project> list() {
        return projectService.listAll();
    }

    @GetMapping("/{projectKey}")
    public ResponseEntity<Project> get(@PathVariable("projectKey") String projectKey) {
        Project project = projectService.findByKey(projectKey);
        return project != null ? ResponseEntity.ok(project) : ResponseEntity.notFound().build();
    }
}

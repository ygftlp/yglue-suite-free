package org.yglue.flow.orch.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.service.ProjectService;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final ProjectService projectService;

    public HealthController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/health")
    public Map<String, Object> globalHealth() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/projects/{projectKey}/health")
    public ResponseEntity<Map<String, Object>> projectHealth(@PathVariable("projectKey") String projectKey) {
        Project project = projectService.findByKey(projectKey);
        if (project == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "status", "DOWN",
                            "reason", "Project not found",
                            "projectKey", projectKey,
                            "timestamp", Instant.now().toString()
                    ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "projectKey", project.getKey(),
                "projectName", project.getName(),
                "timestamp", Instant.now().toString()
        ));
    }
}

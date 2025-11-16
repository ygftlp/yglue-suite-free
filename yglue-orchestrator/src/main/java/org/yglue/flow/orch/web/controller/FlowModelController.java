package org.yglue.flow.orch.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.FlowModel;
import org.yglue.flow.orch.service.FlowModelService;
import org.yglue.flow.orch.service.ProjectService;
import org.yglue.flow.orch.web.dto.flow.response.FlowModelResponse;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectKey}/models")
public class FlowModelController {

    private final ProjectService projectService;
    private final FlowModelService flowModelService;

    public FlowModelController(ProjectService projectService,
                               FlowModelService flowModelService) {
        this.projectService = projectService;
        this.flowModelService = flowModelService;
    }

    @GetMapping
    public List<FlowModelResponse> list(@PathVariable("projectKey") String projectKey) {
        var project = projectService.requireProject(projectKey);
        List<FlowModel> models = flowModelService.listActive(project.getId());
        return models.stream()
                .map(FlowModelResponse::from)
                .collect(Collectors.toList());
    }
}





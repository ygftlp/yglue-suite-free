package org.yglue.flow.orch.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.FlowResolver;
import org.yglue.flow.orch.service.FlowResolverService;
import org.yglue.flow.orch.service.ProjectService;
import org.yglue.flow.orch.web.dto.flow.response.FlowResolverResponse;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectKey}/resolvers")
public class FlowResolverController {

    private final ProjectService projectService;
    private final FlowResolverService flowResolverService;

    public FlowResolverController(ProjectService projectService,
                                  FlowResolverService flowResolverService) {
        this.projectService = projectService;
        this.flowResolverService = flowResolverService;
    }

    @GetMapping
    public List<FlowResolverResponse> list(@PathVariable("projectKey") String projectKey) {
        var project = projectService.requireProject(projectKey);
        List<FlowResolver> resolvers = flowResolverService.listActive(project.getId());
        return resolvers.stream()
                .map(FlowResolverResponse::from)
                .collect(Collectors.toList());
    }
}






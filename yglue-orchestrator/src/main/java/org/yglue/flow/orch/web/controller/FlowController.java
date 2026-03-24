package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.Flow;
import org.yglue.flow.orch.domain.FlowVersion;
import org.yglue.flow.orch.service.FlowAuthoringService;
import org.yglue.flow.orch.service.FlowService;
import org.yglue.flow.orch.web.dto.flow.request.FlowSaveRequest;
import org.yglue.flow.orch.web.dto.flow.request.FlowPublishRequest;
import org.yglue.flow.orch.web.dto.flow.response.FlowServiceSignatureIssueResponse;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectKey}/flows")
public class FlowController {

    private final FlowService flowService;
    private final FlowAuthoringService flowAuthoringService;

    public FlowController(FlowService flowService,
                          FlowAuthoringService flowAuthoringService) {
        this.flowService = flowService;
        this.flowAuthoringService = flowAuthoringService;
    }

    @PostMapping
    public FlowVersion save(@PathVariable("projectKey") String projectKey, @RequestBody @Valid FlowSaveRequest req) {
        return flowAuthoringService.saveFlow(projectKey, req);
    }

    @PostMapping("/{code}/publish")
    public FlowVersion publish(@PathVariable("projectKey") String projectKey,
                               @PathVariable("code") String code,
                               @RequestBody @Valid FlowPublishRequest req) {
        return flowAuthoringService.publishFlow(projectKey, code, req);
    }

    @GetMapping
    public List<Flow> list(@PathVariable("projectKey") String projectKey) {
        return flowService.listFlows(projectKey);
    }

    @GetMapping("/{code}/versions")
    public List<FlowVersion> versions(@PathVariable("projectKey") String projectKey,
                                      @PathVariable("code") String code) {
        return flowService.listVersions(projectKey, code);
    }

    @GetMapping("/{code}/versions/{ver}")
    public FlowVersion getVersion(@PathVariable("projectKey") String projectKey,
                                  @PathVariable("code") String code,
                                  @PathVariable("ver") Integer version) {
        return flowService.getVersion(projectKey, code, version);
    }

    @GetMapping("/issues/service-signatures")
    public List<FlowServiceSignatureIssueResponse> listServiceSignatureIssues(
            @PathVariable("projectKey") String projectKey) {
        return flowService.listServiceSignatureIssues(projectKey);
    }
}

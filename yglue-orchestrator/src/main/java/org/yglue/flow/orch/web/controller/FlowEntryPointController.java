package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.FlowEntryPoint;
import org.yglue.flow.orch.service.FlowEntryPointService;
import org.yglue.flow.orch.web.dto.flow.request.FlowEntryPointToggleRequest;
import org.yglue.flow.orch.web.dto.flow.response.EntryPointsResponse;
import org.yglue.flow.orch.web.dto.flow.response.RestEntryPointResponse;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectKey}/entrypoints")
public class FlowEntryPointController {

    private final FlowEntryPointService flowEntryPointService;

    public FlowEntryPointController(FlowEntryPointService flowEntryPointService) {
        this.flowEntryPointService = flowEntryPointService;
    }

    @GetMapping
    public EntryPointsResponse list(@PathVariable("projectKey") String projectKey) {
        List<FlowEntryPoint> entryPoints = flowEntryPointService.listByProject(projectKey);
        List<RestEntryPointResponse> rests = entryPoints.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new EntryPointsResponse(rests);
    }

    @GetMapping("/flows/{flowCode}")
    public RestEntryPointResponse getByFlowCode(@PathVariable("projectKey") String projectKey,
                                                 @PathVariable("flowCode") String flowCode) {
        FlowEntryPoint entryPoint = flowEntryPointService.getByProjectAndFlow(projectKey, flowCode);
        if (entryPoint == null) {
            return null;
        }
        return toResponse(entryPoint);
    }

    @PatchMapping("/{entrypointId}")
    public RestEntryPointResponse toggle(@PathVariable("projectKey") String projectKey,
                                         @PathVariable("entrypointId") Long entrypointId,
                                         @Valid @RequestBody FlowEntryPointToggleRequest request) {
        FlowEntryPoint updated = flowEntryPointService.updateEnabled(
                projectKey,
                entrypointId,
                request.getEnabled(),
                request.getUpdatedBy());
        return toResponse(updated);
    }

    private RestEntryPointResponse toResponse(FlowEntryPoint entryPoint) {
        boolean replaceResponse = Boolean.TRUE.equals(entryPoint.getReplaceResponse());
        boolean enabled = entryPoint.getEnabled() == null || entryPoint.getEnabled();
        return new RestEntryPointResponse(
                entryPoint.getId(),
                entryPoint.getPath(),
                entryPoint.getHttpMethod(),
                entryPoint.getFlowCode(),
                entryPoint.getRequestSchemaJson(),
                replaceResponse,
                enabled);
    }
}

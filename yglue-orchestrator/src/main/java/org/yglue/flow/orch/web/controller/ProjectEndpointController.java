package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.FlowEntryPoint;
import org.yglue.flow.orch.domain.ProjectEndpoint;
import org.yglue.flow.orch.service.FlowEntryPointService;
import org.yglue.flow.orch.service.ProjectEndpointService;
import org.yglue.flow.orch.web.dto.endpoint.request.ProjectEndpointCreateRequest;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectComponentGroupResponse;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectComponentItemResponse;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectEndpointResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectKey}/endpoints")
public class ProjectEndpointController {

    private static final Map<String, String> COMPONENT_DISPLAY_NAMES = Map.of(
            "BUSINESS", "业务组件",
            "SYSTEM", "系统组件",
            "VALIDATOR", "自定义校验器组件"
    );

    private static final Map<String, Integer> COMPONENT_ORDER = Map.of(
            "BUSINESS", 0,
            "SYSTEM", 1,
            "VALIDATOR", 2
    );

    private final ProjectEndpointService endpointService;
    private final FlowEntryPointService flowEntryPointService;

    public ProjectEndpointController(ProjectEndpointService endpointService,
                                     FlowEntryPointService flowEntryPointService) {
        this.endpointService = endpointService;
        this.flowEntryPointService = flowEntryPointService;
    }

    @GetMapping
    public List<ProjectEndpointResponse> list(@PathVariable("projectKey") String projectKey) {
        return buildEndpointResponses(projectKey);
    }

    @GetMapping("/rests")
    public List<ProjectEndpointResponse> listRests(@PathVariable("projectKey") String projectKey) {
        return buildEndpointResponses(projectKey).stream()
                .filter(endpoint -> "REST".equalsIgnoreCase(endpoint.getEndpointType()))
                .collect(Collectors.toList());
    }

    @GetMapping("/components")
    public List<ProjectComponentGroupResponse> listComponents(@PathVariable("projectKey") String projectKey) {
        List<ProjectEndpointResponse> endpoints = buildEndpointResponses(projectKey);
        if (endpoints.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<ProjectEndpointResponse>> grouped = endpoints.stream()
                .collect(Collectors.groupingBy(
                        ep -> resolveComponentType(ep.getComponentType(), ep.getEndpointType())));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<ProjectEndpointResponse> sortedEndpoints = new ArrayList<>(entry.getValue());
                    sortedEndpoints.sort(Comparator.comparing(
                            ep -> StringUtils.hasText(ep.getName()) ? ep.getName() : "",
                            String.CASE_INSENSITIVE_ORDER));

                    List<ProjectComponentItemResponse> items = sortedEndpoints.stream()
                            .map(this::toComponentItem)
                            .collect(Collectors.toList());

                    String typeKey = entry.getKey();
                    String displayName = COMPONENT_DISPLAY_NAMES.getOrDefault(typeKey, typeKey);
                    return new ProjectComponentGroupResponse(typeKey, displayName, items);
                })
                .sorted(Comparator.comparingInt(g ->
                        COMPONENT_ORDER.getOrDefault(g.getType(), Integer.MAX_VALUE)))
                .collect(Collectors.toList());
    }

    @GetMapping("/{endpointId}")
    public ProjectEndpointResponse get(@PathVariable("projectKey") String projectKey,
                                       @PathVariable("endpointId") Long endpointId) {
        ProjectEndpoint endpoint = endpointService.get(projectKey, endpointId);
        Map<String, FlowEntryPoint> entryPointMap = loadEntrypointMap(projectKey);
        return toResponse(endpoint, entryPointMap);
    }

    @PostMapping
    public ProjectEndpointResponse create(@PathVariable("projectKey") String projectKey,
                                          @RequestBody @Valid ProjectEndpointCreateRequest request) {
        ProjectEndpoint created = endpointService.create(projectKey, request);
        Map<String, FlowEntryPoint> entryPointMap = loadEntrypointMap(projectKey);
        return toResponse(created, entryPointMap);
    }

    private List<ProjectEndpointResponse> buildEndpointResponses(String projectKey) {
        List<ProjectEndpoint> endpoints = endpointService.list(projectKey);
        if (endpoints.isEmpty()) {
            return List.of();
        }
        Map<String, FlowEntryPoint> entryPointMap = loadEntrypointMap(projectKey);
        return endpoints.stream()
                .map(endpoint -> toResponse(endpoint, entryPointMap))
                .collect(Collectors.toList());
    }

    private ProjectComponentItemResponse toComponentItem(ProjectEndpointResponse endpoint) {
        ProjectComponentItemResponse item = new ProjectComponentItemResponse();
        item.setId(endpoint.getId());
        item.setName(endpoint.getName());
        item.setDescription(endpoint.getDescription());
        item.setEndpointType(endpoint.getEndpointType());
        item.setComponentType(resolveComponentType(endpoint.getComponentType(), endpoint.getEndpointType()));
        item.setMethod(endpoint.getMethod());
        item.setPath(endpoint.getPath());
        item.setConfigJson(endpoint.getConfigJson());
        item.setCreateTime(endpoint.getCreateTime());
        item.setUpdateTime(endpoint.getUpdateTime());
        item.setEntrypointId(endpoint.getEntrypointId());
        item.setFlowCode(endpoint.getFlowCode());
        item.setEnabled(endpoint.getEnabled());
        item.setReplaceResponse(endpoint.getReplaceResponse());
        return item;
    }

    private Map<String, FlowEntryPoint> loadEntrypointMap(String projectKey) {
        List<FlowEntryPoint> entryPoints = flowEntryPointService.listByProject(projectKey);
        return entryPoints.stream()
                .filter(ep -> composeKey(ep.getHttpMethod(), ep.getPath()) != null)
                .collect(Collectors.toMap(
                        ep -> composeKey(ep.getHttpMethod(), ep.getPath()),
                        ep -> ep,
                        (existing, replacement) -> existing));
    }

    private ProjectEndpointResponse toResponse(ProjectEndpoint endpoint,
                                               Map<String, FlowEntryPoint> entryPointMap) {
        ProjectEndpointResponse response = ProjectEndpointResponse.from(endpoint);
        
        if ("REST".equalsIgnoreCase(endpoint.getEndpointType())) {
            String key = composeKey(endpoint.getMethod(), endpoint.getPath());
            FlowEntryPoint entryPoint = key != null ? entryPointMap.get(key) : null;
            
            if (entryPoint != null) {
                response.setEntrypointId(entryPoint.getId());
                response.setFlowCode(entryPoint.getFlowCode());
                response.setEnabled(entryPoint.getEnabled() == null || entryPoint.getEnabled());
                response.setReplaceResponse(entryPoint.getReplaceResponse() == null
                        ? Boolean.TRUE
                        : entryPoint.getReplaceResponse());
            } else {
                response.setEnabled(Boolean.FALSE);
                response.setReplaceResponse(Boolean.FALSE);
            }
        }
        
        response.setComponentType(resolveComponentType(response.getComponentType(), response.getEndpointType()));
        return response;
    }

    private String composeKey(String method, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalizedPath = normalizePath(path);
        String normalizedMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        return normalizedMethod + "|" + normalizedPath;
    }

    private String normalizePath(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (trimmed.isEmpty()) {
            return "/";
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        trimmed = trimmed.replaceAll("//+", "/");
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String resolveComponentType(String componentType, String endpointType) {
        if (StringUtils.hasText(componentType)) {
            return componentType.trim().toUpperCase(Locale.ROOT);
        }
        
        if ("REST".equalsIgnoreCase(endpointType)) {
            return "SYSTEM";
        }
        
        if ("FLOW_OPERATION".equalsIgnoreCase(endpointType) 
                || "FLOW_API".equalsIgnoreCase(endpointType)) {
            return "BUSINESS";
        }
        
        return "BUSINESS";
    }
}

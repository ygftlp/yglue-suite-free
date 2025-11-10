package org.yglue.flow.orch.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.ProjectEndpoint;
import org.yglue.flow.orch.persistence.mapper.ProjectEndpointMapper;
import org.yglue.flow.orch.web.dto.endpoint.request.ProjectEndpointCreateRequest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ProjectEndpointService {

    private static final String REST_TYPE = "REST";
    private static final String AUTO_SYNC = "auto-sync";

    private final ProjectService projectService;
    private final ProjectEndpointMapper endpointMapper;

    public ProjectEndpointService(ProjectService projectService,
                                  ProjectEndpointMapper endpointMapper) {
        this.projectService = projectService;
        this.endpointMapper = endpointMapper;
    }

    public List<ProjectEndpoint> list(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        return endpointMapper.selectByProjectId(project.getId());
    }

    public ProjectEndpoint get(String projectKey, Long endpointId) {
        Project project = projectService.requireProject(projectKey);
        ProjectEndpoint endpoint = endpointMapper.selectById(endpointId);
        if (endpoint == null || !endpoint.getProjectId().equals(project.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Endpoint not found: " + endpointId);
        }
        return endpoint;
    }

    @Transactional
    public ProjectEndpoint create(String projectKey, ProjectEndpointCreateRequest request) {
        Project project = projectService.requireProject(projectKey);

        String endpointType = determineEndpointType(request.endpointType);
        String componentType = resolveComponentType(request.componentType, endpointType);
        String method = normalize(request.method);
        String path = normalizePath(request.path);

        if (REST_TYPE.equals(endpointType)) {
            if (!StringUtils.hasText(method)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REST endpoint requires method");
            }
            if (!StringUtils.hasText(path)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REST endpoint requires path");
            }
            method = method.toUpperCase();
        }

        ProjectEndpoint existing = endpointMapper.selectByProjectAndKey(project.getId(), endpointType, method, path);
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Endpoint already exists for project");
        }

        ProjectEndpoint endpoint = new ProjectEndpoint();
        endpoint.setProjectId(project.getId());
        endpoint.setEndpointType(endpointType);
        endpoint.setComponentType(componentType);
        endpoint.setMethod(method);
        endpoint.setPath(path);
        endpoint.setName(request.name.trim());
        endpoint.setDescription(normalize(request.description));
        endpoint.setConfigJson(normalize(request.configJson));
        endpoint.setCreateBy(normalize(request.createdBy));
        endpoint.setUpdateBy(normalize(request.createdBy));
        endpoint.setDelFlag(0);
        endpointMapper.insert(endpoint);
        return endpoint;
    }

    @Transactional
    public void syncEndpoints(Long projectId, List<EndpointPayload> endpoints) {
        List<ProjectEndpoint> existing = endpointMapper.selectByProjectId(projectId);
        Map<String, ProjectEndpoint> existingMap = new HashMap<>();
        for (ProjectEndpoint endpoint : existing) {
            existingMap.put(composeKey(endpoint.getEndpointType(), endpoint.getMethod(), endpoint.getPath()), endpoint);
        }

        Set<Long> retained = new HashSet<>();
        for (EndpointPayload payload : endpoints) {
            String endpointType = determineEndpointType(payload.endpointType());
            String componentType = resolveComponentType(payload.componentType(), endpointType);
            String method = normalize(payload.methodKey());
            String path = normalize(payload.pathKey());

            if (REST_TYPE.equals(endpointType)) {
                if (!StringUtils.hasText(method)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REST endpoint requires method");
                }
                if (!StringUtils.hasText(path)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REST endpoint requires path");
                }
                method = method.toUpperCase();
                path = normalizePath(path);
            }

            String key = composeKey(endpointType, method, path);
            ProjectEndpoint current = existingMap.get(key);
            if (current == null) {
                ProjectEndpoint created = new ProjectEndpoint();
                created.setProjectId(projectId);
                created.setEndpointType(endpointType);
                created.setComponentType(componentType);
                created.setMethod(method);
                created.setPath(path);
                created.setName(payload.displayName());
                created.setDescription(payload.description());
                created.setConfigJson(payload.configJson());
                created.setCreateBy(AUTO_SYNC);
                created.setUpdateBy(AUTO_SYNC);
                created.setDelFlag(0);
                endpointMapper.insert(created);
            } else {
                retained.add(current.getId());
                boolean changed = !Objects.equals(current.getEndpointType(), endpointType)
                        || !Objects.equals(normalize(current.getComponentType()), normalize(componentType))
                        || !Objects.equals(current.getMethod(), method)
                        || !Objects.equals(current.getPath(), path)
                        || !Objects.equals(current.getName(), payload.displayName())
                        || !Objects.equals(current.getDescription(), payload.description())
                        || !Objects.equals(current.getConfigJson(), payload.configJson())
                        || current.getDelFlag() == null || current.getDelFlag() != 0;
                if (changed) {
                    current.setEndpointType(endpointType);
                    current.setComponentType(componentType);
                    current.setMethod(method);
                    current.setPath(path);
                    current.setName(payload.displayName());
                    current.setDescription(payload.description());
                    current.setConfigJson(payload.configJson());
                    current.setDelFlag(0);
                    current.setUpdateBy(AUTO_SYNC);
                    endpointMapper.update(current);
                }
            }
        }

        for (ProjectEndpoint endpoint : existing) {
            if (endpoint.getId() != null && !retained.contains(endpoint.getId())) {
                endpointMapper.softDelete(endpoint.getId(), AUTO_SYNC);
            }
        }
    }

    private String resolveComponentType(String requested, String endpointType) {
        if (StringUtils.hasText(requested)) {
            return requested.trim().toUpperCase();
        }
        if (REST_TYPE.equals(endpointType)) {
            return "SYSTEM";
        }
        if ("FLOW_OPERATION".equalsIgnoreCase(endpointType) || "FLOW_API".equalsIgnoreCase(endpointType)) {
            return "BUSINESS";
        }
        return "BUSINESS";
    }

    private static String composeKey(String type, String method, String path) {
        String keyType = type == null ? "" : type;
        String keyMethod = method == null ? "" : method;
        String keyPath = path == null ? "" : path;
        return keyType + "|" + keyMethod + "|" + keyPath;
    }

    private String determineEndpointType(String type) {
        if (!StringUtils.hasText(type)) {
            return REST_TYPE;
        }
        return type.trim().toUpperCase();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        trimmed = trimmed.replaceAll("//+", "/");
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public record EndpointPayload(String endpointType,
                                   String componentType,
                                   String methodKey,
                                   String pathKey,
                                   String displayName,
                                   String description,
                                   String configJson) {}
}

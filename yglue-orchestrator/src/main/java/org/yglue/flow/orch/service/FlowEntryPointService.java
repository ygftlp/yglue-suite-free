package org.yglue.flow.orch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.FlowEntryPoint;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.persistence.mapper.FlowEntryPointMapper;
import org.yglue.flow.orch.web.dto.flow.request.FlowEntryPointRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FlowEntryPointService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProjectService projectService;
    private final FlowEntryPointMapper mapper;

    public FlowEntryPointService(ProjectService projectService,
                                 FlowEntryPointMapper mapper) {
        this.projectService = projectService;
        this.mapper = mapper;
    }

    @Transactional
    public void replaceEntryPoints(Project project,
                                   String flowCode,
                                   FlowEntryPointRequest request,
                                    String operator) {
        FlowEntryPoint existing = mapper.selectByProjectAndFlow(project.getId(), flowCode);
        if (request == null || request.getPath() == null || request.getPath().isBlank()) {
            if (existing != null && (existing.getDelFlag() == null || existing.getDelFlag() == 0)) {
                mapper.deleteByProjectAndFlow(project.getId(), flowCode, operator);
            }
            return;
        }
        if (existing == null) {
            FlowEntryPoint entry = new FlowEntryPoint();
            entry.setProjectId(project.getId());
            entry.setFlowCode(flowCode);
            entry.setPath(normalizePath(request.getPath()));
            entry.setHttpMethod(normalizeMethod(request.getMethod()));
            entry.setRequestSchemaJson(normalizeSchemaJson(request.getRequestSchemaJson()));
            entry.setReplaceResponse(request.getReplaceResponse() == null
                    ? Boolean.TRUE
                    : request.getReplaceResponse());
            entry.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
            entry.setCreateBy(operator);
            entry.setUpdateBy(operator);
            entry.setDelFlag(0);
            mapper.insert(entry);
        } else {
            existing.setPath(normalizePath(request.getPath()));
            existing.setHttpMethod(normalizeMethod(request.getMethod()));
            existing.setRequestSchemaJson(normalizeSchemaJson(request.getRequestSchemaJson()));
            existing.setReplaceResponse(request.getReplaceResponse() == null
                    ? Boolean.TRUE
                    : request.getReplaceResponse());
            existing.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
            existing.setUpdateBy(operator);
            existing.setDelFlag(0);
            mapper.update(existing);
        }
    }

    public List<FlowEntryPoint> listByProject(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        return new ArrayList<>(mapper.selectByProject(project.getId()));
    }

    public FlowEntryPoint getByProjectAndFlow(String projectKey, String flowCode) {
        Project project = projectService.requireProject(projectKey);
        FlowEntryPoint entryPoint = mapper.selectByProjectAndFlow(project.getId(), flowCode);
        if (entryPoint != null && (entryPoint.getDelFlag() == null || entryPoint.getDelFlag() == 0)) {
            return entryPoint;
        }
        return null;
    }

    @Transactional
    public FlowEntryPoint updateEnabled(String projectKey,
                                        Long entryPointId,
                                        boolean enabled,
                                        String operator) {
        Project project = projectService.requireProject(projectKey);
        FlowEntryPoint entryPoint = mapper.selectById(entryPointId);
        if (entryPoint == null
                || entryPoint.getDelFlag() != null && entryPoint.getDelFlag() == 1
                || !project.getId().equals(entryPoint.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Entry point not found: " + entryPointId);
        }
        if (entryPoint.getFlowCode() == null || entryPoint.getFlowCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Entry point is not bound to any published flow.");
        }
        boolean current = entryPoint.getEnabled() == null || entryPoint.getEnabled();
        if (current != enabled) {
            int affected = mapper.updateEnabled(entryPointId, enabled, operator);
            if (affected == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Failed to update entry point state, please retry.");
            }
            entryPoint.setEnabled(enabled);
            entryPoint.setUpdateBy(operator);
            entryPoint.setUpdateTime(Instant.now());
        }
        return entryPoint;
    }

    private String normalizePath(String path) {
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed.replaceAll("//+", "/");
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSchemaJson(String rawSchema) {
        if (rawSchema == null) {
            return null;
        }
        String trimmed = rawSchema.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            JsonNode tree = OBJECT_MAPPER.readTree(trimmed);
            return OBJECT_MAPPER.writeValueAsString(tree);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestSchema must be valid JSON", ex);
        }
    }
}

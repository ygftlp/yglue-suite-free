package org.yglue.flow.orch.web.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.code.ProjectClassAggregate;
import org.yglue.flow.orch.domain.code.ProjectJarDependency;
import org.yglue.flow.orch.domain.jar.JarClassAggregate;
import org.yglue.flow.orch.persistence.mapper.JarLibraryMapper;
import org.yglue.flow.orch.persistence.mapper.ProjectCodeMapper;
import org.yglue.flow.orch.service.ProjectCodeService;
import org.yglue.flow.orch.service.ProjectService;
import org.yglue.flow.orch.web.dto.snapshot.request.CodeSnapshotUploadRequest;
import org.yglue.flow.orch.web.dto.snapshot.response.CodeSnapshotUploadResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectKey}/code-snapshots")
public class ProjectCodeController {

    private static final int DEFAULT_HELPER_LIMIT = 800;
    private static final int MAX_HELPER_LIMIT = 2000;

    private final ProjectCodeService projectCodeService;
    private final ProjectCodeMapper projectCodeMapper;
    private final JarLibraryMapper jarLibraryMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public ProjectCodeController(ProjectCodeService projectCodeService,
                                 ProjectCodeMapper projectCodeMapper,
                                 JarLibraryMapper jarLibraryMapper,
                                 ProjectService projectService,
                                 ObjectMapper objectMapper) {
        this.projectCodeService = projectCodeService;
        this.projectCodeMapper = projectCodeMapper;
        this.jarLibraryMapper = jarLibraryMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public CodeSnapshotUploadResponse upload(@PathVariable("projectKey") String projectKey,
                                             @RequestBody @Valid CodeSnapshotUploadRequest request) {
        ProjectCodeService.SaveResult result = projectCodeService.save(projectKey, projectKey, request);
        Project project = projectService.requireProject(projectKey);

        CodeSnapshotUploadResponse response = new CodeSnapshotUploadResponse();
        response.setSnapshotId(null);
        response.setProjectId(project.getId());
        response.setProjectCreated(result.projectCreated());
        return response;
    }

    @GetMapping("/helpers")
    public Map<String, Object> helpers(@PathVariable("projectKey") String projectKey,
                                       @RequestParam(name = "endpointId", required = false) Long endpointId,
                                       @RequestParam(name = "limit", required = false, defaultValue = "" + DEFAULT_HELPER_LIMIT) int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        Project project = projectService.requireProject(projectKey);
        int classLimit = Math.max(100, Math.min(MAX_HELPER_LIMIT, limit));

        result.put("snapshotId", null);

        List<ProjectJarDependency> deps = projectCodeMapper.listValidDependenciesByProject(project.getId());
        List<ProjectJarDependency> selectedDeps = deps.stream()
                .filter(d -> Boolean.TRUE.equals(d.getSelected()))
                .toList();
        result.put("selectedJars", selectedDeps.stream().map(this::toSelectedJarItem).toList());

        List<String> jarKeys = selectedDeps.stream()
                .map(ProjectJarDependency::getJarKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();

        List<ProjectClassAggregate> projectClasses =
                projectCodeMapper.listValidAggregatesByProjectLimited(project.getId(), classLimit);

        int remainingForJar = Math.max(0, classLimit - projectClasses.size());
        List<JarClassAggregate> jarClasses = (jarKeys.isEmpty() || remainingForJar == 0)
                ? List.of()
                : jarLibraryMapper.listJarAggregatesByJarKeysLimited(jarKeys, remainingForJar);

        Map<String, Map<String, Object>> deduplicated = new LinkedHashMap<>();
        projectClasses.forEach(c -> addClassRef(deduplicated, c.getQualifiedName(), c.getSimpleName(), c.getPackageName(), c.getKind()));
        jarClasses.forEach(c -> addClassRef(deduplicated, c.getQualifiedName(), c.getSimpleName(), c.getPackageName(), c.getKind()));

        List<Map<String, Object>> classes = deduplicated.values().stream().limit(classLimit).toList();
        result.put("classes", classes);
        return result;
    }

    @GetMapping("/class-members")
    public Map<String, Object> classMembers(@PathVariable("projectKey") String projectKey,
                                            @RequestParam("qualifiedName") String qualifiedName,
                                            @RequestParam("kind") String kind,
                                            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
                                            @RequestParam(name = "size", required = false, defaultValue = "50") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        Project project = projectService.requireProject(projectKey);

        if (qualifiedName == null || qualifiedName.isBlank()) {
            result.put("items", List.of());
            return result;
        }

        int limit = Math.max(1, Math.min(500, size));
        int offset = Math.max(0, (Math.max(1, page) - 1) * limit);
        boolean fieldsQuery = "fields".equalsIgnoreCase(kind);

        ProjectClassAggregate agg = projectCodeMapper.selectAggregateByProjectAndName(project.getId(), qualifiedName);
        if (agg != null) {
            List<Map<String, Object>> items = fieldsQuery
                    ? parseMembersJson(agg.getFieldsJson())
                    : parseMembersJson(agg.getMethodsJson());
            result.put("items", paginate(items, offset, limit));
            return result;
        }

        List<String> jarKeys = projectCodeMapper.listValidDependenciesByProject(project.getId()).stream()
                .filter(d -> Boolean.TRUE.equals(d.getSelected()))
                .map(ProjectJarDependency::getJarKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        if (jarKeys.isEmpty()) {
            result.put("items", List.of());
            return result;
        }

        JarClassAggregate jarClass = jarLibraryMapper.selectJarAggregateByQualifiedNameAndJarKeys(qualifiedName, jarKeys);
        if (jarClass == null) {
            result.put("items", List.of());
            return result;
        }

        List<Map<String, Object>> items = fieldsQuery
                ? parseMembersJson(jarClass.getFieldsJson())
                : parseMembersJson(jarClass.getMethodsJson());
        result.put("items", paginate(items, offset, limit));
        return result;
    }

    @GetMapping("/class-aggregate")
    public Map<String, Object> classAggregate(@PathVariable("projectKey") String projectKey,
                                              @RequestParam("qualifiedName") String qualifiedName) {
        Map<String, Object> result = new LinkedHashMap<>();
        Project project = projectService.requireProject(projectKey);
        if (qualifiedName == null || qualifiedName.isBlank()) {
            result.put("fields", List.of());
            result.put("methods", List.of());
            return result;
        }

        ProjectClassAggregate agg = projectCodeMapper.selectAggregateByProjectAndName(project.getId(), qualifiedName);
        if (agg == null) {
            result.put("fields", List.of());
            result.put("methods", List.of());
            return result;
        }

        List<Map<String, Object>> fields = parseMembersJson(agg.getFieldsJson());
        List<Map<String, Object>> methods = parseMembersJson(agg.getMethodsJson());
        result.put("qualifiedName", agg.getQualifiedName());
        result.put("simpleName", agg.getSimpleName());
        result.put("packageName", agg.getPackageName());
        result.put("kind", agg.getKind());
        result.put("doc", agg.getDoc());
        result.put("fields", fields);
        result.put("methods", methods);
        return result;
    }

    private void addClassRef(Map<String, Map<String, Object>> sink,
                             String qualifiedName,
                             String simpleName,
                             String packageName,
                             String kind) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return;
        }
        if (sink.containsKey(qualifiedName)) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("qualifiedName", qualifiedName);
        item.put("simpleName", simpleName);
        item.put("packageName", packageName);
        item.put("kind", kind);
        sink.put(qualifiedName, item);
    }

    private Map<String, Object> toSelectedJarItem(ProjectJarDependency dep) {
        Map<String, Object> item = new LinkedHashMap<>();
        String coordinate = buildCoordinate(dep);
        item.put("jarId", null);
        item.put("name", resolveJarName(dep, coordinate));
        item.put("coordinate", coordinate);
        item.put("jarKey", dep.getJarKey());
        item.put("groupId", dep.getGroupId());
        item.put("artifactId", dep.getArtifactId());
        item.put("version", dep.getVersion());
        return item;
    }

    private String resolveJarName(ProjectJarDependency dep, String coordinate) {
        if (dep.getArtifactId() != null && !dep.getArtifactId().isBlank()) {
            return dep.getArtifactId();
        }
        if (dep.getJarKey() != null && !dep.getJarKey().isBlank()) {
            return dep.getJarKey();
        }
        return coordinate;
    }

    private String buildCoordinate(ProjectJarDependency dep) {
        if (dep.getJarKey() != null && !dep.getJarKey().isBlank()) {
            return dep.getJarKey();
        }
        List<String> parts = new ArrayList<>(3);
        if (dep.getGroupId() != null && !dep.getGroupId().isBlank()) {
            parts.add(dep.getGroupId());
        }
        if (dep.getArtifactId() != null && !dep.getArtifactId().isBlank()) {
            parts.add(dep.getArtifactId());
        }
        if (dep.getVersion() != null && !dep.getVersion().isBlank()) {
            parts.add(dep.getVersion());
        }
        return String.join(":", parts);
    }

    private List<Map<String, Object>> parseMembersJson(String json) {
        try {
            String raw = (json == null || json.isBlank()) ? "[]" : json;
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> paginate(List<Map<String, Object>> items, int offset, int limit) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int from = Math.min(offset, items.size());
        int to = Math.min(from + limit, items.size());
        return items.subList(from, to);
    }
}

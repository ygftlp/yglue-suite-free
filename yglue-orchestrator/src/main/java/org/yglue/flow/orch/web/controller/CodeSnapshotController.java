package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.snapshot.ProjectCodeSnapshot;
import org.yglue.flow.orch.service.CodeSnapshotService;
import org.yglue.flow.orch.service.CodeSnapshotService.SaveResult;
import org.yglue.flow.orch.web.dto.snapshot.request.CodeSnapshotUploadRequest;
import org.yglue.flow.orch.web.dto.snapshot.response.CodeSnapshotUploadResponse;

@RestController
@RequestMapping("/api/projects/{projectKey}/code-snapshots")
public class CodeSnapshotController {

    private final CodeSnapshotService codeSnapshotService;
    private final org.yglue.flow.orch.persistence.mapper.CodeSnapshotMapper codeSnapshotMapper;
    private final org.yglue.flow.orch.persistence.mapper.JarLibraryMapper jarLibraryMapper;
    private final org.yglue.flow.orch.service.ProjectService projectService;

    public CodeSnapshotController(CodeSnapshotService codeSnapshotService,
                                   org.yglue.flow.orch.persistence.mapper.CodeSnapshotMapper codeSnapshotMapper,
                                   org.yglue.flow.orch.persistence.mapper.JarLibraryMapper jarLibraryMapper,
                                   org.yglue.flow.orch.service.ProjectService projectService) {
        this.codeSnapshotService = codeSnapshotService;
        this.codeSnapshotMapper = codeSnapshotMapper;
        this.jarLibraryMapper = jarLibraryMapper;
        this.projectService = projectService;
    }

    @PostMapping
    public CodeSnapshotUploadResponse upload(@PathVariable("projectKey") String projectKey,
                                             @RequestBody @Valid CodeSnapshotUploadRequest request) {
        SaveResult result = codeSnapshotService.save(projectKey, projectKey, request);
        ProjectCodeSnapshot snapshot = result.snapshot();
        CodeSnapshotUploadResponse response = new CodeSnapshotUploadResponse();
        response.setSnapshotId(snapshot.getId());
        response.setProjectId(snapshot.getProjectId());
        response.setProjectCreated(result.projectCreated());
        return response;
    }

    @org.springframework.web.bind.annotation.GetMapping("/latest")
    public org.yglue.flow.orch.domain.snapshot.ProjectCodeSnapshot latest(
            @org.springframework.web.bind.annotation.PathVariable("projectKey") String projectKey) {
        org.yglue.flow.orch.domain.Project project = projectService.requireProject(projectKey);
        return codeSnapshotMapper.selectLatestByProjectId(project.getId());
    }

    @org.springframework.web.bind.annotation.GetMapping("/helpers")
    public java.util.Map<String, Object> helpers(
            @org.springframework.web.bind.annotation.PathVariable("projectKey") String projectKey,
            @org.springframework.web.bind.annotation.RequestParam(name = "endpointId", required = false) java.lang.Long endpointId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        org.yglue.flow.orch.domain.Project project = projectService.requireProject(projectKey);
        org.yglue.flow.orch.domain.snapshot.ProjectCodeSnapshot snapshot = codeSnapshotMapper.selectLatestByProjectId(project.getId());
        if (snapshot == null) {
            result.put("snapshotId", null);
            result.put("classes", java.util.List.of());
            result.put("selectedJars", java.util.List.of());
            return result;
        }
        result.put("snapshotId", snapshot.getId());

        java.util.List<org.yglue.flow.orch.domain.snapshot.ProjectSnapshotDependency> deps =
                codeSnapshotMapper.listSnapshotDependencies(snapshot.getId());
        java.util.List<java.lang.Long> jarIds = deps.stream()
                .filter(d -> java.lang.Boolean.TRUE.equals(d.getSelected()))
                .map(org.yglue.flow.orch.domain.snapshot.ProjectSnapshotDependency::getJarId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        result.put("selectedJars", deps.stream()
                .filter(d -> java.lang.Boolean.TRUE.equals(d.getSelected()))
                .map(d -> java.util.Map.of(
                        "jarId", d.getJarId(),
                        "name", d.getName(),
                        "coordinate", d.getCoordinate()))
                .toList());

        java.util.List<org.yglue.flow.orch.domain.jar.JarLibraryClass> classes = jarLibraryMapper.listJarClassesByJarIds(jarIds);
        result.put("classes", classes.stream()
                .map(c -> java.util.Map.of(
                        "qualifiedName", c.getQualifiedName(),
                        "simpleName", c.getSimpleName(),
                        "packageName", c.getPackageName(),
                        "kind", c.getKind()))
                .toList());
        return result;
    }

    @org.springframework.web.bind.annotation.GetMapping("/class-members")
    public java.util.Map<String, Object> classMembers(
            @org.springframework.web.bind.annotation.PathVariable("projectKey") String projectKey,
            @org.springframework.web.bind.annotation.RequestParam("qualifiedName") String qualifiedName,
            @org.springframework.web.bind.annotation.RequestParam("kind") String kind,
            @org.springframework.web.bind.annotation.RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(name = "size", required = false, defaultValue = "50") int size) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        org.yglue.flow.orch.domain.Project project = projectService.requireProject(projectKey);
        org.yglue.flow.orch.domain.snapshot.ProjectCodeSnapshot snapshot = codeSnapshotMapper.selectLatestByProjectId(project.getId());
        if (snapshot == null || qualifiedName == null || qualifiedName.isBlank()) {
            result.put("items", java.util.List.of());
            return result;
        }
        java.util.List<org.yglue.flow.orch.domain.snapshot.ProjectSnapshotDependency> deps =
                codeSnapshotMapper.listSnapshotDependencies(snapshot.getId());
        java.util.List<java.lang.Long> jarIds = deps.stream()
                .filter(d -> java.lang.Boolean.TRUE.equals(d.getSelected()))
                .map(org.yglue.flow.orch.domain.snapshot.ProjectSnapshotDependency::getJarId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        org.yglue.flow.orch.domain.jar.JarLibraryClass clazz = jarLibraryMapper.selectJarClassByQualifiedNameAndJarIds(qualifiedName, jarIds);
        if (clazz == null) {
            result.put("items", java.util.List.of());
            return result;
        }
        int limit = Math.max(1, Math.min(500, size));
        int offset = Math.max(0, (Math.max(1, page) - 1) * limit);
        if ("fields".equalsIgnoreCase(kind)) {
            java.util.List<org.yglue.flow.orch.domain.jar.JarLibraryClassField> fields = jarLibraryMapper.listJarClassFields(clazz.getId(), limit, offset);
            result.put("items", fields.stream().map(f -> java.util.Map.of(
                    "name", f.getName(),
                    "type", f.getType(),
                    "static", f.getStatic()
            )).toList());
        } else {
            java.util.List<org.yglue.flow.orch.domain.jar.JarLibraryClassMethod> methods = jarLibraryMapper.listJarClassMethods(clazz.getId(), limit, offset);
            result.put("items", methods.stream().map(m -> java.util.Map.of(
                    "name", m.getName(),
                    "returnType", m.getReturnType(),
                    "static", m.getStatic(),
                    "parametersJson", m.getParametersJson()
            )).toList());
        }
        return result;
    }
}

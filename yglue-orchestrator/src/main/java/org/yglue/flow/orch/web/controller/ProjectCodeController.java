package org.yglue.flow.orch.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.yglue.flow.orch.service.ProjectCodeService;
import org.yglue.flow.orch.service.ProjectCodeService.SaveResult;
import org.yglue.flow.orch.web.dto.snapshot.request.CodeSnapshotUploadRequest;
import org.yglue.flow.orch.web.dto.snapshot.response.CodeSnapshotUploadResponse;

@RestController
@RequestMapping("/api/projects/{projectKey}/code-snapshots")
public class ProjectCodeController {

    private final ProjectCodeService projectCodeService;
    private final org.yglue.flow.orch.persistence.mapper.ProjectCodeMapper projectCodeMapper;
    private final org.yglue.flow.orch.persistence.mapper.JarLibraryMapper jarLibraryMapper;
    private final org.yglue.flow.orch.service.ProjectService projectService;
    private final ObjectMapper objectMapper;

    public ProjectCodeController(ProjectCodeService projectCodeService,
                                 org.yglue.flow.orch.persistence.mapper.ProjectCodeMapper projectCodeMapper,
                                 org.yglue.flow.orch.persistence.mapper.JarLibraryMapper jarLibraryMapper,
                                 org.yglue.flow.orch.service.ProjectService projectService,
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
        SaveResult result = projectCodeService.save(projectKey, projectKey, request);
        org.yglue.flow.orch.domain.Project project = projectService.requireProject(projectKey);
        CodeSnapshotUploadResponse response = new CodeSnapshotUploadResponse();
        response.setSnapshotId(null);
        response.setProjectId(project.getId());
        response.setProjectCreated(result.projectCreated());
        return response;
    }


    @org.springframework.web.bind.annotation.GetMapping("/helpers")
    public java.util.Map<String, Object> helpers(
            @org.springframework.web.bind.annotation.PathVariable("projectKey") String projectKey,
            @org.springframework.web.bind.annotation.RequestParam(name = "endpointId", required = false) java.lang.Long endpointId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        org.yglue.flow.orch.domain.Project project = projectService.requireProject(projectKey);
        
        // 新逻辑：直接查询项目的有效数据
        result.put("snapshotId", null);  // 不再使用 snapshotId

        // 1. 查询依赖的 jar 包
        java.util.List<org.yglue.flow.orch.domain.code.ProjectJarDependency> deps =
                projectCodeMapper.listValidDependenciesByProject(project.getId());
        java.util.List<String> jarKeys = deps.stream()
                .filter(d -> java.lang.Boolean.TRUE.equals(d.getSelected()))
                .map(org.yglue.flow.orch.domain.code.ProjectJarDependency::getJarKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        result.put("selectedJars", deps.stream()
                .filter(d -> java.lang.Boolean.TRUE.equals(d.getSelected()))
                .map(d -> java.util.Map.of(
                        "jarKey", d.getJarKey(),
                        "groupId", d.getGroupId(),
                        "artifactId", d.getArtifactId(),
                        "version", d.getVersion()))
                .toList());

        // 2. 查询项目类（来自 yglue_project_class_agg 表）
        java.util.List<org.yglue.flow.orch.domain.code.ProjectClassAggregate> projectClasses =
                projectCodeMapper.listValidAggregatesByProject(project.getId());
        
        // 3. 根据 jar_key 查询 jar 包中的类（来自 yglue_jar_class_agg 表）
        java.util.List<org.yglue.flow.orch.domain.jar.JarClassAggregate> jarClasses = 
                jarKeys.isEmpty() ? java.util.List.of() : jarLibraryMapper.listJarAggregatesByJarKeys(jarKeys);
        
        // 4. 合并项目类和 jar 类
        java.util.List<java.util.Map<String, Object>> allClasses = new java.util.ArrayList<>();
        
        // 添加项目类（仅名称信息）
        projectClasses.forEach(c -> allClasses.add(java.util.Map.of(
                "qualifiedName", c.getQualifiedName(),
                "simpleName", c.getSimpleName(),
                "packageName", c.getPackageName(),
                "kind", c.getKind())));
        
        // 添加 jar 类
        jarClasses.forEach(c -> allClasses.add(java.util.Map.of(
                "qualifiedName", c.getQualifiedName(),
                "simpleName", c.getSimpleName(),
                "packageName", c.getPackageName(),
                "kind", c.getKind())));
        
        result.put("classes", allClasses);
        return result;
    }

    @org.springframework.web.bind.annotation.GetMapping("/class-members")
    public java.util.Map<String, Object> classMembers(
            @org.springframework.web.bind.annotation.PathVariable("projectKey") String projectKey,
            @org.springframework.web.bind.annotation.RequestParam("qualifiedName") String qualifiedName,
            @org.springframework.web.bind.annotation.RequestParam("kind") String kind,
            @org.springframework.web.bind.annotation.RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(name = "size", required = false, defaultValue = "50") int size) throws JsonProcessingException {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        org.yglue.flow.orch.domain.Project project = projectService.requireProject(projectKey);
        
        if (qualifiedName == null || qualifiedName.isBlank()) {
            result.put("items", java.util.List.of());
            return result;
        }
        
        int limit = Math.max(1, Math.min(500, size));
        int offset = Math.max(0, (Math.max(1, page) - 1) * limit);
        
        // 1. 优先查询聚合类（yglue_project_class_agg 表）
        org.yglue.flow.orch.domain.code.ProjectClassAggregate agg =
                projectCodeMapper.selectAggregateByProjectAndName(project.getId(), qualifiedName);
        if (agg != null) {
            java.util.List<java.util.Map<String, Object>> items;
            try {
                if ("fields".equalsIgnoreCase(kind)) {
                    items = objectMapper.readValue(
                            agg.getFieldsJson() == null ? "[]" : agg.getFieldsJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {}
                    );
                } else {
                    items = objectMapper.readValue(
                            agg.getMethodsJson() == null ? "[]" : agg.getMethodsJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {}
                    );
                }
            } catch (Exception e) {
                items = java.util.List.of();
            }
            // 分页
            int from = Math.min(offset, items.size());
            int to = Math.min(from + limit, items.size());
            result.put("items", items.subList(from, to));
            return result;
        }
        
        // 2. 项目类不存在，尝试查询 jar 包中的类
        java.util.List<org.yglue.flow.orch.domain.code.ProjectJarDependency> deps =
                projectCodeMapper.listValidDependenciesByProject(project.getId());
        java.util.List<String> jarKeys = deps.stream()
                .filter(d -> java.lang.Boolean.TRUE.equals(d.getSelected()))
                .map(org.yglue.flow.orch.domain.code.ProjectJarDependency::getJarKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        
        if (jarKeys.isEmpty()) {
            result.put("items", java.util.List.of());
            return result;
        }
        
        org.yglue.flow.orch.domain.jar.JarClassAggregate jarClass = 
                jarLibraryMapper.selectJarAggregateByQualifiedNameAndJarKeys(qualifiedName, jarKeys);
        if (jarClass == null) {
            result.put("items", java.util.List.of());
            return result;
        }
        
        if ("fields".equalsIgnoreCase(kind)) {
            java.util.List<java.util.Map<String, Object>> fields =
                    objectMapper.readValue(
                                                jarClass.getFieldsJson() == null ? "[]" : jarClass.getFieldsJson(),
                                                new TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            result.put("items", fields);
        } else {
            java.util.List<java.util.Map<String, Object>> methods =
                    objectMapper.readValue(
                                                jarClass.getMethodsJson() == null ? "[]" : jarClass.getMethodsJson(),
                                                new TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            result.put("items", methods);
        }
        return result;
    }

    @org.springframework.web.bind.annotation.GetMapping("/class-aggregate")
    public java.util.Map<String, Object> classAggregate(
            @org.springframework.web.bind.annotation.PathVariable("projectKey") String projectKey,
            @org.springframework.web.bind.annotation.RequestParam("qualifiedName") String qualifiedName) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        org.yglue.flow.orch.domain.Project project = projectService.requireProject(projectKey);
        if (qualifiedName == null || qualifiedName.isBlank()) {
            result.put("fields", java.util.List.of());
            result.put("methods", java.util.List.of());
            return result;
        }
        org.yglue.flow.orch.domain.code.ProjectClassAggregate agg =
                projectCodeMapper.selectAggregateByProjectAndName(project.getId(), qualifiedName);
        if (agg == null) {
            result.put("fields", java.util.List.of());
            result.put("methods", java.util.List.of());
            return result;
        }
        // 直接返回聚合的 JSON（对象数组）
        java.util.List<java.util.Map<String, Object>> fields;
        java.util.List<java.util.Map<String, Object>> methods;
        try {
            fields = objectMapper.readValue(agg.getFieldsJson() == null ? "[]" : agg.getFieldsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            methods = objectMapper.readValue(agg.getMethodsJson() == null ? "[]" : agg.getMethodsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
        } catch (Exception e) {
            fields = java.util.List.of();
            methods = java.util.List.of();
        }
        result.put("qualifiedName", agg.getQualifiedName());
        result.put("simpleName", agg.getSimpleName());
        result.put("packageName", agg.getPackageName());
        result.put("kind", agg.getKind());
        result.put("doc", agg.getDoc());
        result.put("fields", fields);
        result.put("methods", methods);
        return result;
    }
}

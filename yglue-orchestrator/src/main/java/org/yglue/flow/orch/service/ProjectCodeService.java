package org.yglue.flow.orch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.jar.JarLibrary;
import org.yglue.flow.orch.domain.code.ProjectCodeSnapshot;
import org.yglue.flow.orch.domain.code.ProjectJarDependency;
import org.yglue.flow.orch.domain.code.ProjectClassAggregate;
import org.yglue.flow.orch.persistence.mapper.ProjectCodeMapper;
import org.yglue.flow.orch.persistence.mapper.JarLibraryMapper;
import org.yglue.flow.orch.web.dto.snapshot.request.CodeSnapshotUploadRequest;

@Service
public class ProjectCodeService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProjectService projectService;
    private final ProjectCodeMapper projectCodeMapper;
    private final JarLibraryMapper jarLibraryMapper;

    public ProjectCodeService(ProjectService projectService,
                              ProjectCodeMapper projectCodeMapper,
                              JarLibraryMapper jarLibraryMapper) {
        this.projectService = projectService;
        this.projectCodeMapper = projectCodeMapper;
        this.jarLibraryMapper = jarLibraryMapper;
    }

    private static JarLibrary getJarLibrary(CodeSnapshotUploadRequest.JarMetadata metadata, JarLibrary existing) {
        JarLibrary jarLibrary = existing != null ? existing : new JarLibrary();
        jarLibrary.setJarKey(metadata.getId());
        jarLibrary.setName(metadata.getName());
        jarLibrary.setGroupId(metadata.getGroupId());
        jarLibrary.setArtifactId(metadata.getArtifactId());
        jarLibrary.setVersion(metadata.getVersion());
        jarLibrary.setJarType("LIBRARY");
        jarLibrary.setSource("IDEA");
        jarLibrary.setDescription(metadata.getCoordinate());
        jarLibrary.setContentHash(metadata.getContentHash());
        return jarLibrary;
    }

    @Transactional
    public SaveResult save(String projectKey, String fallbackProjectName, CodeSnapshotUploadRequest request) {
        ProjectService.EnsureResult ensureResult = projectService.ensureProjectWithFlag(
                projectKey,
                request.getProjectName() != null && !request.getProjectName().isBlank()
                        ? request.getProjectName()
                        : fallbackProjectName
        );
        Project project = ensureResult.project();

        // 不再记录快照
        ProjectCodeSnapshot snapshot = null;

        // 新逻辑：先标记聚合类为无效，再 UPSERT 聚合
        projectCodeMapper.markAllAggregatesInvalid(project.getId());
        projectCodeMapper.markAllDependenciesInvalid(project.getId());

        Map<String, Long> jarIdMap = upsertJarLibraries(request.getJarMetadata());
        upsertClassAggregates(project.getId(), request.getClasses());
        upsertDependencies(project.getId(), request.getDependencies(),
                request.getSelectedJars(), jarIdMap);

        return new SaveResult(snapshot, ensureResult.created());
    }

    /**
     * UPSERT 项目依赖
     */
    private void upsertDependencies(Long projectId,
                                    List<CodeSnapshotUploadRequest.DependencyItem> dependencies,
                                    List<String> selectedJars,
                                    Map<String, Long> jarIdMap) {
        if (dependencies == null) {
            return;
        }
        Set<String> selected = selectedJars == null
                ? Set.of()
                : new HashSet<>(selectedJars);
        for (CodeSnapshotUploadRequest.DependencyItem dep : dependencies) {
            ProjectJarDependency dependency = new ProjectJarDependency();
            dependency.setProjectId(projectId);
            dependency.setGroupId(dep.getGroupId());
            dependency.setArtifactId(dep.getArtifactId());
            dependency.setVersion(dep.getVersion());
            // jar_key 即为 Maven 坐标 (groupId:artifactId:version)
            dependency.setJarKey(dep.getCoordinate());
            dependency.setScope(dep.getScope());
            dependency.setSelected(selected.contains(dep.getDependencyId()));
            dependency.setIsValid(true);
            projectCodeMapper.upsertDependency(dependency);
        }
    }

    /**
     * UPSERT 项目类聚合（按类存储 methods/fields JSON）
     */
    private void upsertClassAggregates(Long projectId, List<CodeSnapshotUploadRequest.ClassItem> classes) {
        if (classes == null) {
            return;
        }
        for (CodeSnapshotUploadRequest.ClassItem classItem : classes) {
            ProjectClassAggregate agg = new ProjectClassAggregate();
            agg.setProjectId(projectId);
            agg.setQualifiedName(classItem.getQualifiedName());
            agg.setSimpleName(classItem.getSimpleName());
            agg.setPackageName(classItem.getPackageName());
            agg.setKind(classItem.getKind());
            agg.setSourceType(classItem.getSourceType() == null ? "PROJECT" : classItem.getSourceType());
            agg.setDoc(classItem.getDoc());
            agg.setFieldsJson(toJsonFields(classItem.getFields()));
            agg.setMethodsJson(toJsonMethods(classItem.getMethods()));
            agg.setIsValid(true);
            projectCodeMapper.upsertClassAggregate(agg);
        }
    }

    private String toJsonFields(List<CodeSnapshotUploadRequest.FieldItem> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(
                    fields.stream()
                            .filter(Objects::nonNull) // 过滤null元素
                            .map(f -> {
                                Map<String, Object> fieldMap = new HashMap<>();
                                // 安全获取值，null值会保留为JSON的null
                                fieldMap.put("name", f.getName());
                                fieldMap.put("type", f.getType());
                                // 安全处理Boolean类型，防止f.getIsStatic()返回null
                                fieldMap.put("static", Boolean.TRUE.equals(f.getIsStatic()));
                                fieldMap.put("doc", f.getDoc());
                                return fieldMap;
                            })
                            .collect(Collectors.toList())
            );
        } catch (JsonProcessingException e) {
            // 更具体的异常类型和消息
            throw new IllegalStateException("Failed to serialize fields: " + e.getMessage(), e);
        }
    }

    private String toJsonMethods(List<CodeSnapshotUploadRequest.MethodItem> methods) {
        if (methods == null || methods.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(
                    methods.stream()
                            .filter(Objects::nonNull) // 过滤null方法
                            .map(m -> {
                                Map<String, Object> methodMap = new HashMap<>();

                                // 安全添加基础属性
                                methodMap.put("name", m.getName());
                                methodMap.put("returnType", m.getReturnType());
                                methodMap.put("static", Boolean.TRUE.equals(m.getIsStatic()));
                                methodMap.put("doc", m.getDoc());

                                // 安全处理参数列表
                                List<Map<String, String>> parameters = Optional.ofNullable(m.getParameters())
                                        .orElseGet(Collections::emptyList) // 返回空列表而不是null
                                        .stream()
                                        .filter(Objects::nonNull) // 过滤null参数
                                        .map(p -> {
                                            Map<String, String> paramMap = new HashMap<>();
                                            // 安全添加参数属性
                                            paramMap.put("name", p.getName());
                                            paramMap.put("type", p.getType());
                                            return paramMap;
                                        })
                                        .collect(Collectors.toList());

                                methodMap.put("parameters", parameters);
                                return methodMap;
                            })
                            .collect(Collectors.toList())
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize methods: " + e.getMessage(), e);
        }
    }

    private Map<String, Long> upsertJarLibraries(List<CodeSnapshotUploadRequest.JarMetadata> jarMetadata) {
        Map<String, Long> result = new HashMap<>();
        if (jarMetadata == null) {
            return result;
        }
        for (CodeSnapshotUploadRequest.JarMetadata metadata : jarMetadata) {
            if (metadata.getId() == null || metadata.getId().isBlank()) {
                continue;
            }
            JarLibrary existing = jarLibraryMapper.selectByJarKey(metadata.getId());
            JarLibrary jarLibrary = getJarLibrary(metadata, existing);

            boolean needRefresh = existing == null || !Objects.equals(existing.getContentHash(), metadata.getContentHash());
            if (existing == null) {
                jarLibraryMapper.insertJar(jarLibrary);
            } else {
                jarLibraryMapper.updateJar(jarLibrary);
            }

            if (needRefresh) {
                jarLibraryMapper.deleteJarClassAggregates(jarLibrary.getId());
                insertJarClasses(jarLibrary.getJarKey(), metadata.getClasses());
            }

            result.put(metadata.getId(), jarLibrary.getId());
        }
        return result;
    }

    private void insertJarClasses(String jarKey, List<CodeSnapshotUploadRequest.JarClassItem> classes) {
        if (classes == null) {
            return;
        }
        // 从id查询jarKey
        JarLibrary jarLibrary = jarLibraryMapper.selectByJarKey(jarKey);
        if (jarLibrary == null) {
            return;
        }

        for (CodeSnapshotUploadRequest.JarClassItem classItem : classes) {
            org.yglue.flow.orch.domain.jar.JarClassAggregate agg = new org.yglue.flow.orch.domain.jar.JarClassAggregate();
            agg.setJarKey(jarKey);  // 使用 jarKey 代替 jarId
            agg.setQualifiedName(classItem.getQualifiedName());
            agg.setSimpleName(classItem.getSimpleName());
            agg.setPackageName(classItem.getPackageName());
            agg.setKind(classItem.getKind());
            agg.setDoc(classItem.getDoc());
            agg.setFieldsJson(toJsonFields(classItem.getFields()));
            agg.setMethodsJson(toJsonMethods(classItem.getMethods()));
            jarLibraryMapper.upsertJarClassAggregate(agg);
        }
    }

    private String writeParameters(List<CodeSnapshotUploadRequest.ParameterItem> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(parameters.stream()
                    .map(p -> Map.of(
                            "name", p.getName(),
                            "type", p.getType()))
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize parameters", e);
        }
    }

    private String normalizeSnapshotKey(String snapshotKey) {
        String key = snapshotKey == null || snapshotKey.isBlank()
                ? "code-" + Instant.now().toEpochMilli()
                : snapshotKey.trim();
        return key.length() > 120 ? key.substring(0, 120) : key;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private String resolveContentHash(String payloadHash, String fallbackSeed) {
        if (payloadHash != null && !payloadHash.isBlank()) {
            return payloadHash;
        }
        return sha256Hex(fallbackSeed + ":" + Instant.now().toEpochMilli());
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes());
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute hash", e);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record SaveResult(ProjectCodeSnapshot snapshot, boolean projectCreated) {
    }
}

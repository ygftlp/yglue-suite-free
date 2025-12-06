package org.yglue.flow.orch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.jar.JarLibrary;
import org.yglue.flow.orch.domain.jar.JarLibraryClass;
import org.yglue.flow.orch.domain.jar.JarLibraryClassField;
import org.yglue.flow.orch.domain.jar.JarLibraryClassMethod;
import org.yglue.flow.orch.domain.snapshot.ProjectCodeSnapshot;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotClass;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotClassField;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotClassMethod;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotDependency;
import org.yglue.flow.orch.persistence.mapper.CodeSnapshotMapper;
import org.yglue.flow.orch.persistence.mapper.JarLibraryMapper;
import org.yglue.flow.orch.web.dto.snapshot.request.CodeSnapshotUploadRequest;

@Service
public class CodeSnapshotService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProjectService projectService;
    private final CodeSnapshotMapper codeSnapshotMapper;
    private final JarLibraryMapper jarLibraryMapper;

    public CodeSnapshotService(ProjectService projectService,
                               CodeSnapshotMapper codeSnapshotMapper,
                               JarLibraryMapper jarLibraryMapper) {
        this.projectService = projectService;
        this.codeSnapshotMapper = codeSnapshotMapper;
        this.jarLibraryMapper = jarLibraryMapper;
    }

    public record SaveResult(ProjectCodeSnapshot snapshot, boolean projectCreated) {}

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
        codeSnapshotMapper.markAllAggregatesInvalid(project.getId());
        codeSnapshotMapper.markAllDependenciesInvalid(project.getId());

        Map<String, Long> jarIdMap = upsertJarLibraries(request.getJarMetadata());
        upsertClassAggregates(project.getId(), request.getClasses());
        upsertDependencies(project.getId(), request.getDependencies(),
                request.getSelectedJars(), jarIdMap);

        return new SaveResult(snapshot, ensureResult.created());
    }

    /**
     * UPSERT 项目类（新逻辑）
     */
    private void upsertClasses(Long projectId, List<CodeSnapshotUploadRequest.ClassItem> classes) {
        if (classes == null) {
            return;
        }
        for (CodeSnapshotUploadRequest.ClassItem classItem : classes) {
            ProjectSnapshotClass snapshotClass = new ProjectSnapshotClass();
            snapshotClass.setProjectId(projectId);
            snapshotClass.setQualifiedName(classItem.getQualifiedName());
            snapshotClass.setSimpleName(classItem.getSimpleName());
            snapshotClass.setPackageName(classItem.getPackageName());
            snapshotClass.setKind(classItem.getKind());
            snapshotClass.setSourceType(classItem.getSourceType() == null ? "PROJECT" : classItem.getSourceType());
            snapshotClass.setIsValid(true);
            codeSnapshotMapper.upsertClass(snapshotClass);

            // 查询类 ID（UPSERT 后需要获取 ID）
            if (snapshotClass.getId() == null) {
                ProjectSnapshotClass inserted = codeSnapshotMapper.selectClassByProjectAndName(
                    projectId, classItem.getQualifiedName());
                if (inserted != null) {
                    snapshotClass.setId(inserted.getId());
                }
            }

            if (classItem.getFields() != null && snapshotClass.getId() != null) {
                for (CodeSnapshotUploadRequest.FieldItem fieldItem : classItem.getFields()) {
                    ProjectSnapshotClassField field = new ProjectSnapshotClassField();
                    field.setClassId(snapshotClass.getId());
                    field.setName(fieldItem.getName());
                    field.setType(fieldItem.getType());
                    field.setStatic(fieldItem.getStatic());
                    field.setIsValid(true);
                    codeSnapshotMapper.upsertField(field);
                }
            }

            if (classItem.getMethods() != null && snapshotClass.getId() != null) {
                for (CodeSnapshotUploadRequest.MethodItem methodItem : classItem.getMethods()) {
                    ProjectSnapshotClassMethod method = new ProjectSnapshotClassMethod();
                    method.setClassId(snapshotClass.getId());
                    method.setName(methodItem.getName());
                    method.setReturnType(methodItem.getReturnType());
                    method.setStatic(methodItem.getStatic());
                    method.setParametersJson(writeParameters(methodItem.getParameters()));
                    
                    // 生成方法签名哈希
                    String hash = ProjectSnapshotClassMethod.generateSignatureHash(
                        method.getName(),
                        method.getReturnType(),
                        method.getParametersJson()
                    );
                    method.setMethodSignatureHash(hash);
                    method.setIsValid(true);
                    
                    codeSnapshotMapper.upsertMethod(method);
                }
            }
        }
    }

    /**
     * UPSERT 项目依赖（新逻辑）
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
            ProjectSnapshotDependency dependency = new ProjectSnapshotDependency();
            dependency.setProjectId(projectId);
            dependency.setDependencyId(dep.getDependencyId());
            dependency.setName(dep.getName());
            dependency.setGroupId(dep.getGroupId());
            dependency.setArtifactId(dep.getArtifactId());
            dependency.setVersion(dep.getVersion());
            dependency.setCoordinate(dep.getCoordinate());
            dependency.setScope(dep.getScope());
            if (dep.getDependencyId() != null) {
                dependency.setJarId(jarIdMap.get(dep.getDependencyId()));
            }
            dependency.setSelected(selected.contains(dep.getDependencyId()));
            dependency.setIsValid(true);
            codeSnapshotMapper.upsertDependency(dependency);
        }
    }

    /**
     * UPSERT 聚合类（按类存储 methods/fields JSON）
     */
    private void upsertClassAggregates(Long projectId, List<CodeSnapshotUploadRequest.ClassItem> classes) {
        if (classes == null) {
            return;
        }
        for (CodeSnapshotUploadRequest.ClassItem classItem : classes) {
            org.yglue.flow.orch.domain.snapshot.ProjectClassAggregate agg = new org.yglue.flow.orch.domain.snapshot.ProjectClassAggregate();
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
            codeSnapshotMapper.upsertClassAggregate(agg);
        }
    }

    private String toJsonFields(List<CodeSnapshotUploadRequest.FieldItem> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(fields.stream()
                    .map(f -> java.util.Map.of(
                            "name", f.getName(),
                            "type", f.getType(),
                            "static", f.getStatic(),
                            "doc", f.getDoc()))
                    .collect(java.util.stream.Collectors.toList()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize fields", e);
        }
    }

    private String toJsonMethods(List<CodeSnapshotUploadRequest.MethodItem> methods) {
        if (methods == null || methods.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(methods.stream()
                    .map(m -> java.util.Map.of(
                            "name", m.getName(),
                            "returnType", m.getReturnType(),
                            "static", m.getStatic(),
                            "doc", m.getDoc(),
                            "parameters", (m.getParameters() == null ? java.util.List.of() : m.getParameters().stream()
                                    .map(p -> java.util.Map.of("name", p.getName(), "type", p.getType()))
                                    .collect(java.util.stream.Collectors.toList()))
                    ))
                    .collect(java.util.stream.Collectors.toList()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize methods", e);
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

            boolean needRefresh = existing == null || !Objects.equals(existing.getContentHash(), metadata.getContentHash());
            if (existing == null) {
                jarLibraryMapper.insertJar(jarLibrary);
            } else {
                jarLibraryMapper.updateJar(jarLibrary);
            }

            if (needRefresh) {
                jarLibraryMapper.deleteJarClassAggregates(jarLibrary.getId());
                insertJarClasses(jarLibrary.getId(), metadata.getClasses());
            }

            result.put(metadata.getId(), jarLibrary.getId());
        }
        return result;
    }

    private void insertJarClasses(Long jarId, List<CodeSnapshotUploadRequest.JarClassItem> classes) {
        if (classes == null) {
            return;
        }
        for (CodeSnapshotUploadRequest.JarClassItem classItem : classes) {
            org.yglue.flow.orch.domain.jar.JarClassAggregate agg = new org.yglue.flow.orch.domain.jar.JarClassAggregate();
            agg.setJarId(jarId);
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
                ? "snapshot-" + Instant.now().toEpochMilli()
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
}

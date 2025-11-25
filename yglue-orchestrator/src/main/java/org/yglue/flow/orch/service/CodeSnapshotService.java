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

        String snapshotKey = normalizeSnapshotKey(request.getSnapshotKey());
        ProjectCodeSnapshot existing = codeSnapshotMapper.selectByProjectAndKey(project.getId(), snapshotKey);
        if (existing != null) {
            codeSnapshotMapper.deleteSnapshotAssociations(existing.getId());
            codeSnapshotMapper.deleteSnapshot(existing.getId());
        }

        ProjectCodeSnapshot snapshot = new ProjectCodeSnapshot();
        snapshot.setProjectId(project.getId());
        snapshot.setSnapshotKey(snapshotKey);
        snapshot.setCommitHash(trimToNull(request.getCommitHash()));
        snapshot.setGeneratedAt(parseInstant(request.getGeneratedAt()));
        snapshot.setStatus(1);
        snapshot.setContentHash(resolveContentHash(request.getContentHash(), snapshotKey));
        snapshot.setIdeProduct(request.getIde() != null ? request.getIde().getProductName() : null);
        snapshot.setIdeVersion(request.getIde() != null ? request.getIde().getVersion() : null);
        snapshot.setIdeBuild(request.getIde() != null ? request.getIde().getBuild() : null);
        codeSnapshotMapper.insertSnapshot(snapshot);

        Map<String, Long> jarIdMap = upsertJarLibraries(request.getJarMetadata());
        persistClasses(snapshot.getId(), request.getClasses());
        persistDependencies(snapshot.getId(), request.getDependencies(),
                request.getSelectedJars(), jarIdMap);

        return new SaveResult(snapshot, ensureResult.created());
    }

    private void persistClasses(Long snapshotId, List<CodeSnapshotUploadRequest.ClassItem> classes) {
        if (classes == null) {
            return;
        }
        for (CodeSnapshotUploadRequest.ClassItem classItem : classes) {
            ProjectSnapshotClass snapshotClass = new ProjectSnapshotClass();
            snapshotClass.setSnapshotId(snapshotId);
            snapshotClass.setQualifiedName(classItem.getQualifiedName());
            snapshotClass.setSimpleName(classItem.getSimpleName());
            snapshotClass.setPackageName(classItem.getPackageName());
            snapshotClass.setKind(classItem.getKind());
            snapshotClass.setSourceType(classItem.getSourceType() == null ? "PROJECT" : classItem.getSourceType());
            codeSnapshotMapper.insertSnapshotClass(snapshotClass);

            if (classItem.getFields() != null) {
                for (CodeSnapshotUploadRequest.FieldItem fieldItem : classItem.getFields()) {
                    ProjectSnapshotClassField field = new ProjectSnapshotClassField();
                    field.setClassId(snapshotClass.getId());
                    field.setName(fieldItem.getName());
                    field.setType(fieldItem.getType());
                    field.setStatic(fieldItem.getStatic());
                    codeSnapshotMapper.insertSnapshotClassField(field);
                }
            }

            if (classItem.getMethods() != null) {
                for (CodeSnapshotUploadRequest.MethodItem methodItem : classItem.getMethods()) {
                    ProjectSnapshotClassMethod method = new ProjectSnapshotClassMethod();
                    method.setClassId(snapshotClass.getId());
                    method.setName(methodItem.getName());
                    method.setReturnType(methodItem.getReturnType());
                    method.setStatic(methodItem.getStatic());
                    method.setParametersJson(writeParameters(methodItem.getParameters()));
                    codeSnapshotMapper.insertSnapshotClassMethod(method);
                }
            }
        }
    }

    private void persistDependencies(Long snapshotId,
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
            dependency.setSnapshotId(snapshotId);
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
            codeSnapshotMapper.insertSnapshotDependency(dependency);
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
                jarLibraryMapper.deleteJarClassMethods(jarLibrary.getId());
                jarLibraryMapper.deleteJarClassFields(jarLibrary.getId());
                jarLibraryMapper.deleteJarClasses(jarLibrary.getId());
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
            JarLibraryClass jarClass = new JarLibraryClass();
            jarClass.setJarId(jarId);
            jarClass.setQualifiedName(classItem.getQualifiedName());
            jarClass.setSimpleName(classItem.getSimpleName());
            jarClass.setPackageName(classItem.getPackageName());
            jarClass.setKind(classItem.getKind());
            jarLibraryMapper.insertJarClass(jarClass);

            if (classItem.getFields() != null) {
                for (CodeSnapshotUploadRequest.JarFieldItem fieldItem : classItem.getFields()) {
                    JarLibraryClassField field = new JarLibraryClassField();
                    field.setClassId(jarClass.getId());
                    field.setName(fieldItem.getName());
                    field.setType(fieldItem.getType());
                    field.setStatic(fieldItem.getStatic());
                    jarLibraryMapper.insertJarClassField(field);
                }
            }

            if (classItem.getMethods() != null) {
                for (CodeSnapshotUploadRequest.JarMethodItem methodItem : classItem.getMethods()) {
                    JarLibraryClassMethod method = new JarLibraryClassMethod();
                    method.setClassId(jarClass.getId());
                    method.setName(methodItem.getName());
                    method.setReturnType(methodItem.getReturnType());
                    method.setStatic(methodItem.getStatic());
                    method.setParametersJson(writeParameters(methodItem.getParameters()));
                    jarLibraryMapper.insertJarClassMethod(method);
                }
            }
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

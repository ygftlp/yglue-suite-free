package org.yglue.flow.orch.web.dto.snapshot.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class CodeSnapshotUploadRequest {

    private String projectName;
    private String snapshotKey;
    private String commitHash;
    private String generatedAt;
    @Valid
    private IdeInfo ide;
    @NotNull
    @Valid
    private List<ClassItem> classes = new ArrayList<>();
    @Valid
    private List<DependencyItem> dependencies = new ArrayList<>();
    private List<String> selectedJars = new ArrayList<>();
    @Valid
    private List<JarMetadata> jarMetadata = new ArrayList<>();
    private String contentHash;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getSnapshotKey() {
        return snapshotKey;
    }

    public void setSnapshotKey(String snapshotKey) {
        this.snapshotKey = snapshotKey;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public IdeInfo getIde() {
        return ide;
    }

    public void setIde(IdeInfo ide) {
        this.ide = ide;
    }

    public List<ClassItem> getClasses() {
        return classes;
    }

    public void setClasses(List<ClassItem> classes) {
        this.classes = classes;
    }

    public List<DependencyItem> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<DependencyItem> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getSelectedJars() {
        return selectedJars;
    }

    public void setSelectedJars(List<String> selectedJars) {
        this.selectedJars = selectedJars;
    }

    public List<JarMetadata> getJarMetadata() {
        return jarMetadata;
    }

    public void setJarMetadata(List<JarMetadata> jarMetadata) {
        this.jarMetadata = jarMetadata;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public static class IdeInfo {
        private String platform;
        private String productName;
        private String version;
        private String build;

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getBuild() {
            return build;
        }

        public void setBuild(String build) {
            this.build = build;
        }
    }

    public static class ClassItem {
        private String qualifiedName;
        private String simpleName;
        private String packageName;
        private String kind;
        private String sourceType;
        @Valid
        private List<FieldItem> fields = new ArrayList<>();
        @Valid
        private List<MethodItem> methods = new ArrayList<>();

        public String getQualifiedName() {
            return qualifiedName;
        }

        public void setQualifiedName(String qualifiedName) {
            this.qualifiedName = qualifiedName;
        }

        public String getSimpleName() {
            return simpleName;
        }

        public void setSimpleName(String simpleName) {
            this.simpleName = simpleName;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public List<FieldItem> getFields() {
            return fields;
        }

        public void setFields(List<FieldItem> fields) {
            this.fields = fields;
        }

        public List<MethodItem> getMethods() {
            return methods;
        }

        public void setMethods(List<MethodItem> methods) {
            this.methods = methods;
        }
    }

    public static class FieldItem {
        private String name;
        private String type;
        private Boolean isStatic;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Boolean getStatic() {
            return isStatic;
        }

        public void setStatic(Boolean aStatic) {
            isStatic = aStatic;
        }
    }

    public static class MethodItem {
        private String name;
        private String returnType;
        private Boolean isStatic;
        @Valid
        private List<ParameterItem> parameters = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public Boolean getStatic() {
            return isStatic;
        }

        public void setStatic(Boolean aStatic) {
            isStatic = aStatic;
        }

        public List<ParameterItem> getParameters() {
            return parameters;
        }

        public void setParameters(List<ParameterItem> parameters) {
            this.parameters = parameters;
        }
    }

    public static class ParameterItem {
        private String name;
        private String type;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class DependencyItem {
        private String dependencyId;
        private String name;
        private String groupId;
        private String artifactId;
        private String version;
        private String coordinate;
        private String scope;

        public String getDependencyId() {
            return dependencyId;
        }

        public void setDependencyId(String dependencyId) {
            this.dependencyId = dependencyId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getCoordinate() {
            return coordinate;
        }

        public void setCoordinate(String coordinate) {
            this.coordinate = coordinate;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }

    public static class JarMetadata {
        private String id;
        private String name;
        private String groupId;
        private String artifactId;
        private String version;
        private String coordinate;
        private String contentHash;
        @Valid
        private List<JarClassItem> classes = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getCoordinate() {
            return coordinate;
        }

        public void setCoordinate(String coordinate) {
            this.coordinate = coordinate;
        }

        public String getContentHash() {
            return contentHash;
        }

        public void setContentHash(String contentHash) {
            this.contentHash = contentHash;
        }

        public List<JarClassItem> getClasses() {
            return classes;
        }

        public void setClasses(List<JarClassItem> classes) {
            this.classes = classes;
        }
    }

    public static class JarClassItem {
        private String qualifiedName;
        private String simpleName;
        private String packageName;
        private String kind;
        @Valid
        private List<JarFieldItem> fields = new ArrayList<>();
        @Valid
        private List<JarMethodItem> methods = new ArrayList<>();

        public String getQualifiedName() {
            return qualifiedName;
        }

        public void setQualifiedName(String qualifiedName) {
            this.qualifiedName = qualifiedName;
        }

        public String getSimpleName() {
            return simpleName;
        }

        public void setSimpleName(String simpleName) {
            this.simpleName = simpleName;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public List<JarFieldItem> getFields() {
            return fields;
        }

        public void setFields(List<JarFieldItem> fields) {
            this.fields = fields;
        }

        public List<JarMethodItem> getMethods() {
            return methods;
        }

        public void setMethods(List<JarMethodItem> methods) {
            this.methods = methods;
        }
    }

    public static class JarFieldItem {
        private String name;
        private String type;
        private Boolean isStatic;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Boolean getStatic() {
            return isStatic;
        }

        public void setStatic(Boolean aStatic) {
            isStatic = aStatic;
        }
    }

    public static class JarMethodItem {
        private String name;
        private String returnType;
        private Boolean isStatic;
        @Valid
        private List<ParameterItem> parameters = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public Boolean getStatic() {
            return isStatic;
        }

        public void setStatic(Boolean aStatic) {
            isStatic = aStatic;
        }

        public List<ParameterItem> getParameters() {
            return parameters;
        }

        public void setParameters(List<ParameterItem> parameters) {
            this.parameters = parameters;
        }
    }
}

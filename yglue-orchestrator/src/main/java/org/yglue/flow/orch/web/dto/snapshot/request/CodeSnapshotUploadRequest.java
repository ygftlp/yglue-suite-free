package org.yglue.flow.orch.web.dto.snapshot.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
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

    @Data
    public static class IdeInfo {
        private String platform;
        private String productName;
        private String version;
        private String build;

    }

    @Data
    public static class ClassItem {
        private String qualifiedName;
        private String simpleName;
        private String packageName;
        private String kind;
        private String sourceType;
        private String doc;
        @Valid
        private List<FieldItem> fields = new ArrayList<>();
        @Valid
        private List<MethodItem> methods = new ArrayList<>();
    }

    @Data
    public static class FieldItem {
        private String name;
        private String type;
        private Boolean isStatic;
        private String doc;
    }

    @Data
    public static class MethodItem {
        private String name;
        private String returnType;
        private Boolean isStatic;
        @Valid
        private List<ParameterItem> parameters = new ArrayList<>();
        private String doc;
    }

    @Data
    public static class ParameterItem {
        private String name;
        private String type;
        private String doc;

    }

    @Data
    public static class DependencyItem {
        private String dependencyId;
        private String name;
        private String groupId;
        private String artifactId;
        private String version;
        private String coordinate;
        private String scope;

    }

    @Data
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
    }

    @Data
    public static class JarClassItem {
        private String qualifiedName;
        private String simpleName;
        private String packageName;
        private String kind;
        private String doc;
        @Valid
        private List<FieldItem> fields = new ArrayList<>();
        @Valid
        private List<MethodItem> methods = new ArrayList<>();

    }

    @Data
    public static class JarFieldItem {
        private String name;
        private String type;
        private Boolean isStatic;
        private String doc;

    }

    @Data
    public static class JarMethodItem {
        private String name;
        private String returnType;
        private Boolean isStatic;
        private String doc;
        @Valid
        private List<ParameterItem> parameters = new ArrayList<>();
    }
}

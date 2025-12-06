package org.yglue.flow.orch.domain.code;

public class ProjectClassAggregate {
    private Long id;
    private Long projectId;
    private String qualifiedName;
    private String simpleName;
    private String packageName;
    private String kind;
    private String sourceType;
    private Long jarId;
    private String doc;
    private String fieldsJson;
    private String methodsJson;
    private Boolean isValid;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getQualifiedName() { return qualifiedName; }
    public void setQualifiedName(String qualifiedName) { this.qualifiedName = qualifiedName; }

    public String getSimpleName() { return simpleName; }
    public void setSimpleName(String simpleName) { this.simpleName = simpleName; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public Long getJarId() { return jarId; }
    public void setJarId(Long jarId) { this.jarId = jarId; }

    public String getDoc() { return doc; }
    public void setDoc(String doc) { this.doc = doc; }

    public String getFieldsJson() { return fieldsJson; }
    public void setFieldsJson(String fieldsJson) { this.fieldsJson = fieldsJson; }

    public String getMethodsJson() { return methodsJson; }
    public void setMethodsJson(String methodsJson) { this.methodsJson = methodsJson; }

    public Boolean getIsValid() { return isValid; }
    public void setIsValid(Boolean isValid) { this.isValid = isValid; }

    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }

    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }
}

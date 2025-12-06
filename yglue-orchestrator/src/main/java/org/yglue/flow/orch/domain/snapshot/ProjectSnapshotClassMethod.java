package org.yglue.flow.orch.domain.snapshot;

public class ProjectSnapshotClassMethod {
    private Long id;
    private Long classId;
    private String name;
    private String returnType;
    private Boolean isStatic;
    private String parametersJson;
    private String methodSignatureHash;  // 方法签名哈希值（用于唯一性判断）
    private Boolean isValid;  // 是否有效

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

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

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public String getMethodSignatureHash() {
        return methodSignatureHash;
    }

    public void setMethodSignatureHash(String methodSignatureHash) {
        this.methodSignatureHash = methodSignatureHash;
    }

    public Boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(Boolean isValid) {
        this.isValid = isValid;
    }

    /**
     * 生成方法签名哈希值
     * 使用 SHA-256 算法对 name + returnType + parametersJson 进行哈希
     */
    public static String generateSignatureHash(String name, String returnType, String parametersJson) {
        try {
            String signature = (name != null ? name : "") + 
                             (returnType != null ? returnType : "") + 
                             (parametersJson != null ? parametersJson : "");
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signature.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("生成方法签名哈希失败", e);
        }
    }
}

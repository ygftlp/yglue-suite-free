package org.yglue.flow.orch.domain.code;

import lombok.Data;

@Data
public class ProjectSnapshotClassMethod {
    private Long id;
    private Long classId;
    private String name;
    private String returnType;
    private Boolean isStatic;
    private String parametersJson;
    private String methodSignatureHash;  // 方法签名哈希值（用于唯一性判断）
    private Boolean isValid;  // 是否有效


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

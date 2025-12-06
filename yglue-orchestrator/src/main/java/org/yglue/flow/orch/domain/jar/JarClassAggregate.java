package org.yglue.flow.orch.domain.jar;

import lombok.Data;

@Data
public class JarClassAggregate {
    private Long id;
    private String jarKey;  // 改为 jar_key,支持并行处理
    private String qualifiedName;
    private String simpleName;
    private String packageName;
    private String kind;
    private String doc;
    private String fieldsJson;
    private String methodsJson;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;
}

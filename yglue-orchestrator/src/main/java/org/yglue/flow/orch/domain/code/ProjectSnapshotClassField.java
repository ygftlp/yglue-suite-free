package org.yglue.flow.orch.domain.code;

import lombok.Data;

@Data
public class ProjectSnapshotClassField {
    private Long id;
    private Long classId;
    private String name;
    private String type;
    private Boolean isStatic;
    private Boolean isValid;  // 是否有效

}

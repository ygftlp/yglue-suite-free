package org.yglue.flow.orch.domain;

import lombok.Data;

import java.util.Date;

@Data
public class FlowModel {
    private Long id;
    private Long projectId;
    private String identifier;
    private String name;
    private String className;
    private String description;
    private String category;
    private String version;
    private String tagsJson;
    private String schemaJson;
    private String rawJson;
    private Integer delFlag;
    private Date createTime;
    private Date updateTime;
}





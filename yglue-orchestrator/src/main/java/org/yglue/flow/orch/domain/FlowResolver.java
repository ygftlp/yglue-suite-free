package org.yglue.flow.orch.domain;

import lombok.Data;

import java.util.Date;

@Data
public class FlowResolver {
    private Long id;
    private Long projectId;
    private String type;
    private String name;
    private String description;
    private String category;
    private Integer builtin;
    private String configSchema;
    private String className;
    private String rawJson;
    private Integer delFlag;
    private Date createTime;
    private Date updateTime;
}





package org.yglue.flow.orch.domain;

import lombok.Data;

import java.util.Date;

@Data
public class ProjectEndpoint {
    private Long id;
    private Long projectId;
    private String endpointType;
    private String componentType;
    private String method;
    private String path;
    private String name;
    private String description;
    private String configJson;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    private Integer delFlag;
}

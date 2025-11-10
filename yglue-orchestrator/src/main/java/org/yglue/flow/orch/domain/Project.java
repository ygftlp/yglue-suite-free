package org.yglue.flow.orch.domain;


import lombok.Data;

import java.util.Date;

@Data
public class Project {
    private Long id;
    private String key;
    private String name;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    private Integer delFlag;
}

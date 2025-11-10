package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.Project;

import java.util.List;

@Mapper
public interface ProjectMapper {
    void insert(Project project);

    Project selectByKey(@Param("projectKey") String projectKey);

    Project selectById(@Param("id") Long id);

    List<Project> selectAll();
}

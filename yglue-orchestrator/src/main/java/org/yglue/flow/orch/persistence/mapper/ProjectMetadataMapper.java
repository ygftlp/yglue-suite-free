package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.ProjectMetadata;

import java.util.List;

@Mapper
public interface ProjectMetadataMapper {
    void insert(ProjectMetadata metadata);

    List<ProjectMetadata> selectByProject(@Param("projectId") Long projectId);
}

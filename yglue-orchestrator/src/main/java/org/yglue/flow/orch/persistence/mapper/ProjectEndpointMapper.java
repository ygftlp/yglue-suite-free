package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.ProjectEndpoint;

import java.util.List;

@Mapper
public interface ProjectEndpointMapper {
    void insert(ProjectEndpoint endpoint);

    ProjectEndpoint selectById(@Param("id") Long id);

    List<ProjectEndpoint> selectByProjectId(@Param("projectId") Long projectId);

    ProjectEndpoint selectByProjectAndKey(@Param("projectId") Long projectId,
                                          @Param("endpointType") String endpointType,
                                          @Param("method") String method,
                                          @Param("path") String path);

    void update(ProjectEndpoint endpoint);

    void softDelete(@Param("id") Long id, @Param("updateBy") String updateBy);
}


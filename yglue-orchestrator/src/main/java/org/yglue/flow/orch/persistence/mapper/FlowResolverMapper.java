package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.FlowResolver;

import java.util.List;

@Mapper
public interface FlowResolverMapper {

    void insert(FlowResolver resolver);

    void update(FlowResolver resolver);

    FlowResolver selectByProjectAndType(@Param("projectId") Long projectId,
                                        @Param("type") String type);

    List<FlowResolver> selectByProjectId(@Param("projectId") Long projectId);

    List<FlowResolver> selectActiveByProjectId(@Param("projectId") Long projectId);

    void markDeletedByProjectExcludingTypes(@Param("projectId") Long projectId,
                                            @Param("types") List<String> types);
}





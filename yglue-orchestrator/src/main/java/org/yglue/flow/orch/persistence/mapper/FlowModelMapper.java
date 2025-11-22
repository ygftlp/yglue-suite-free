package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.FlowModel;

import java.util.List;

@Mapper
public interface FlowModelMapper {

    void insert(FlowModel model);

    void update(FlowModel model);

    List<FlowModel> selectByProjectId(@Param("projectId") Long projectId);

    FlowModel selectByProjectAndIdentifier(@Param("projectId") Long projectId,
                                           @Param("identifier") String identifier);

    List<FlowModel> selectActiveByProjectId(@Param("projectId") Long projectId);

    void markDeletedByProjectExcludingIdentifiers(@Param("projectId") Long projectId,
                                                  @Param("identifiers") List<String> identifiers);
}






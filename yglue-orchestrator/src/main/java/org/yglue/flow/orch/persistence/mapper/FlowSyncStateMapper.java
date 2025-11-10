package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.FlowSyncState;

import java.util.List;

@Mapper
public interface FlowSyncStateMapper {
    FlowSyncState selectByFlowAndInstance(@Param("flowId") Long flowId,
                                          @Param("instanceKey") String instanceKey);

    void insert(FlowSyncState state);

    void update(FlowSyncState state);

    List<FlowSyncState> selectByProjectAndInstance(@Param("projectId") Long projectId,
                                                   @Param("instanceKey") String instanceKey);
}

package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.FlowVersion;

import java.util.List;

@Mapper
public interface FlowVersionMapper {
    void insert(FlowVersion version);

    List<FlowVersion> selectByFlowIdDesc(@Param("flowId") Long flowId);

    FlowVersion selectByFlowIdAndVersion(@Param("flowId") Long flowId, @Param("versionNo") Integer versionNo);

    Integer selectMaxVersion(@Param("flowId") Long flowId);

    List<FlowVersion> selectByIds(@Param("ids") List<Long> ids);
}

package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.FlowEntryPoint;

import java.util.List;

@Mapper
public interface FlowEntryPointMapper {

    void insert(FlowEntryPoint entryPoint);

    void deleteByProjectAndFlow(@Param("projectId") Long projectId,
                                @Param("flowCode") String flowCode,
                                @Param("updateBy") String updateBy);

    List<FlowEntryPoint> selectByProject(@Param("projectId") Long projectId);

    FlowEntryPoint selectById(@Param("id") Long id);

    FlowEntryPoint selectByProjectAndFlow(@Param("projectId") Long projectId,
                                          @Param("flowCode") String flowCode);

    void update(FlowEntryPoint entryPoint);

    int updateEnabled(@Param("id") Long id,
                      @Param("enabled") boolean enabled,
                      @Param("updateBy") String updateBy);
}

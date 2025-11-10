package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.PluginInstance;

import java.util.List;

@Mapper
public interface PluginInstanceMapper {
    void insert(PluginInstance instance);

    void update(PluginInstance instance);

    PluginInstance selectByProjectAndInstance(@Param("projectId") Long projectId,
                                              @Param("instanceKey") String instanceKey);

    List<PluginInstance> selectByProject(@Param("projectId") Long projectId);
}

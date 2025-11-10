package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.StatEvent;

import java.util.List;

@Mapper
public interface StatEventMapper {
    void insert(StatEvent event);

    List<StatEvent> selectByProject(@Param("projectId") Long projectId);
}

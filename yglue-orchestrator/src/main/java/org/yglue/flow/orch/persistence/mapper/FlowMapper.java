package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.Flow;

import java.util.List;

@Mapper
public interface FlowMapper {
    void insert(Flow flow);

    void update(Flow flow);

    Flow selectById(@Param("id") Long id);

    Flow selectByProjectAndCode(@Param("projectId") Long projectId, @Param("code") String code);

    List<Flow> selectByProject(@Param("projectId") Long projectId);

    void updateLatestVersion(@Param("flowId") Long flowId, @Param("latestVersionId") Long latestVersionId);

    void updatePublishedVersion(@Param("flowId") Long flowId,
                                @Param("publishedVersionId") Long publishedVersionId,
                                @Param("updateBy") String updateBy);
}

package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.code.ProjectJarDependency;
import org.yglue.flow.orch.domain.code.ProjectClassAggregate;

@Mapper
public interface ProjectCodeMapper {

    // =============================
    // 项目 JAR 依赖管理
    // =============================

    /**
     * 标记项目的所有依赖为无效
     */
    void markAllDependenciesInvalid(@Param("projectId") Long projectId);

    /**
     * UPSERT 项目 JAR 依赖
     */
    void upsertDependency(ProjectJarDependency dependency);

    /**
     * 查询项目的所有有效依赖
     */
    java.util.List<ProjectJarDependency> listValidDependenciesByProject(@Param("projectId") Long projectId);

    // =============================
    // 项目类聚合管理（按类存储 methods/fields JSON）
    // =============================

    /**
     * 标记项目所有聚合类为无效
     */
    void markAllAggregatesInvalid(@Param("projectId") Long projectId);

    /**
     * UPSERT 类聚合记录
     */
    void upsertClassAggregate(ProjectClassAggregate aggregate);

    /**
     * 根据项目ID和类名查询聚合类
     */
    ProjectClassAggregate selectAggregateByProjectAndName(@Param("projectId") Long projectId,
                                                           @Param("qualifiedName") String qualifiedName);

    /**
     * 查询项目的所有有效聚合类
     */
    java.util.List<ProjectClassAggregate> listValidAggregatesByProject(@Param("projectId") Long projectId);

    java.util.List<ProjectClassAggregate> listValidAggregatesByProjectLimited(@Param("projectId") Long projectId,
                                                                               @Param("limit") int limit);
}

package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.snapshot.ProjectCodeSnapshot;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotClass;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotClassField;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotClassMethod;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotDependency;
import org.yglue.flow.orch.domain.snapshot.ProjectClassAggregate;

@Mapper
public interface CodeSnapshotMapper {





    void insertSnapshotClass(ProjectSnapshotClass snapshotClass);

    void insertSnapshotClassField(ProjectSnapshotClassField field);

    void insertSnapshotClassMethod(ProjectSnapshotClassMethod method);

    void insertSnapshotDependency(ProjectSnapshotDependency dependency);


    java.util.List<ProjectSnapshotDependency> listSnapshotDependencies(@Param("snapshotId") Long snapshotId);

    /**
     * 查询快照中的项目类列表
     */
    java.util.List<ProjectSnapshotClass> listSnapshotClasses(@Param("snapshotId") Long snapshotId);

    /**
     * 根据类的全限定名查询项目类
     */
    ProjectSnapshotClass selectSnapshotClassByQualifiedName(@Param("snapshotId") Long snapshotId,
                                                            @Param("qualifiedName") String qualifiedName);

    /**
     * 查询项目类的字段列表
     */
    java.util.List<ProjectSnapshotClassField> listSnapshotClassFields(@Param("classId") Long classId,
                                                                      @Param("limit") int limit,
                                                                      @Param("offset") int offset);

    /**
     * 查询项目类的方法列表
     */
    java.util.List<ProjectSnapshotClassMethod> listSnapshotClassMethods(@Param("classId") Long classId,
                                                                        @Param("limit") int limit,
                                                                        @Param("offset") int offset);

    // ========================================
    // 新增：基于 project_id 的方法
    // ========================================

    /**
     * 标记项目的所有类为无效
     */
    void markAllClassesInvalid(@Param("projectId") Long projectId);

    /**
     * 标记项目的所有依赖为无效
     */
    void markAllDependenciesInvalid(@Param("projectId") Long projectId);

    /**
     * UPSERT 项目类
     */
    void upsertClass(ProjectSnapshotClass projectClass);

    /**
     * UPSERT 项目类字段
     */
    void upsertField(ProjectSnapshotClassField field);

    /**
     * UPSERT 项目类方法
     */
    void upsertMethod(ProjectSnapshotClassMethod method);

    /**
     * UPSERT 项目依赖
     */
    void upsertDependency(ProjectSnapshotDependency dependency);

    /**
     * 根据项目ID和类名查询类
     */
    ProjectSnapshotClass selectClassByProjectAndName(@Param("projectId") Long projectId,
                                                     @Param("qualifiedName") String qualifiedName);

    /**
     * 查询项目的所有有效类
     */
    java.util.List<ProjectSnapshotClass> listValidClassesByProject(@Param("projectId") Long projectId);

    /**
     * 查询项目的所有有效依赖
     */
    java.util.List<ProjectSnapshotDependency> listValidDependenciesByProject(@Param("projectId") Long projectId);

    // =============================
    // 聚合类相关（按类存储 methods/fields JSON）
    // =============================

    void markAllAggregatesInvalid(@Param("projectId") Long projectId);

    void upsertClassAggregate(org.yglue.flow.orch.domain.snapshot.ProjectClassAggregate aggregate);

    org.yglue.flow.orch.domain.snapshot.ProjectClassAggregate selectAggregateByProjectAndName(@Param("projectId") Long projectId,
                                                                                              @Param("qualifiedName") String qualifiedName);

    java.util.List<org.yglue.flow.orch.domain.snapshot.ProjectClassAggregate> listValidAggregatesByProject(@Param("projectId") Long projectId);
}


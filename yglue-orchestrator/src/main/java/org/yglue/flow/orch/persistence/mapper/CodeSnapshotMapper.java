package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.snapshot.ProjectCodeSnapshot;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotClass;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotClassField;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotClassMethod;
import org.yglue.flow.orch.domain.snapshot.ProjectSnapshotDependency;

@Mapper
public interface CodeSnapshotMapper {

    ProjectCodeSnapshot selectByProjectAndKey(@Param("projectId") Long projectId,
                                             @Param("snapshotKey") String snapshotKey);

    void insertSnapshot(ProjectCodeSnapshot snapshot);

    void deleteSnapshotAssociations(@Param("snapshotId") Long snapshotId);

    void deleteSnapshot(@Param("snapshotId") Long snapshotId);

    void insertSnapshotClass(ProjectSnapshotClass snapshotClass);

    void insertSnapshotClassField(ProjectSnapshotClassField field);

    void insertSnapshotClassMethod(ProjectSnapshotClassMethod method);

    void insertSnapshotDependency(ProjectSnapshotDependency dependency);
}

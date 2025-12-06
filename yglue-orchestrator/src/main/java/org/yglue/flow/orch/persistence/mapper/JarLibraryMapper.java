package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.jar.JarLibrary;
import org.yglue.flow.orch.domain.jar.JarLibraryClass;
import org.yglue.flow.orch.domain.jar.JarLibraryClassField;
import org.yglue.flow.orch.domain.jar.JarLibraryClassMethod;
import org.yglue.flow.orch.domain.jar.JarClassAggregate;

@Mapper
public interface JarLibraryMapper {

    JarLibrary selectByJarKey(@Param("jarKey") String jarKey);

    void insertJar(JarLibrary jarLibrary);

    void updateJar(JarLibrary jarLibrary);

    void deleteJarClassMethods(@Param("jarId") Long jarId);

    void deleteJarClassFields(@Param("jarId") Long jarId);

    void deleteJarClasses(@Param("jarId") Long jarId);

    void insertJarClass(JarLibraryClass jarClass);

    void insertJarClassField(JarLibraryClassField field);

    void insertJarClassMethod(JarLibraryClassMethod method);

    java.util.List<JarLibraryClass> listJarClassesByJarIds(@Param("jarIds") java.util.List<Long> jarIds);

    JarLibraryClass selectJarClassByQualifiedNameAndJarIds(@Param("qualifiedName") String qualifiedName,
                                                           @Param("jarIds") java.util.List<Long> jarIds);

    java.util.List<JarLibraryClassField> listJarClassFields(@Param("classId") Long classId,
                                                           @Param("limit") int limit,
                                                           @Param("offset") int offset);

    java.util.List<JarLibraryClassMethod> listJarClassMethods(@Param("classId") Long classId,
                                                             @Param("limit") int limit,
                                                             @Param("offset") int offset);

    // =============================
    // 聚合：按 JAR 存储类的 methods/fields JSON
    // =============================

    void deleteJarClassAggregates(@Param("jarId") Long jarId);

    void upsertJarClassAggregate(JarClassAggregate aggregate);

    // 根据 jar_key 列表查询 JAR 类聚合
    java.util.List<JarClassAggregate> listJarAggregatesByJarKeys(@Param("jarKeys") java.util.List<String> jarKeys);

    // 根据 jar_key 列表和类名查询 JAR 类聚合
    JarClassAggregate selectJarAggregateByQualifiedNameAndJarKeys(@Param("qualifiedName") String qualifiedName,
                                                                   @Param("jarKeys") java.util.List<String> jarKeys);

    // @Deprecated - 使用 listJarAggregatesByJarKeys 代替
    java.util.List<JarClassAggregate> listJarAggregatesByJarIds(@Param("jarIds") java.util.List<Long> jarIds);

    // @Deprecated - 使用 selectJarAggregateByQualifiedNameAndJarKeys 代替
    JarClassAggregate selectJarAggregateByQualifiedNameAndJarIds(@Param("qualifiedName") String qualifiedName,
                                                                 @Param("jarIds") java.util.List<Long> jarIds);
}

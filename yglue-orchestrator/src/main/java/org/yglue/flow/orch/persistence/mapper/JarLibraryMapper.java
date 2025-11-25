package org.yglue.flow.orch.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.yglue.flow.orch.domain.jar.JarLibrary;
import org.yglue.flow.orch.domain.jar.JarLibraryClass;
import org.yglue.flow.orch.domain.jar.JarLibraryClassField;
import org.yglue.flow.orch.domain.jar.JarLibraryClassMethod;

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
}

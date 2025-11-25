package org.yglue.flow.idea.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.OrderRootType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 枚举项目的 Jar 依赖并生成唯一标识。
 */
public final class JarDependencyResolver {

    private static final Pattern COORDINATE_PATTERN =
            Pattern.compile("(?:Gradle|Maven)\\s*:\\s*([^:]+):([^:]+):([^:]+)");

    private JarDependencyResolver() {
    }

    public static @NotNull List<JarInfo> listProjectJars(@Nullable Project project) {
        if (project == null) {
            return Collections.emptyList();
        }
        List<JarInfo> jars = new ArrayList<>();
        enumerateLibraries(project).forEach(library -> {
            JarCoordinate coordinate = parseCoordinate(library.getName());
            String id = buildLibraryId(coordinate, library);
            String display = buildDisplayName(coordinate, library);
            jars.add(new JarInfo(id, display, coordinate, library));
            return true;
        });
        return jars;
    }

    public static @NotNull Map<String, Library> mapLibrariesById(@Nullable Project project) {
        if (project == null) {
            return Collections.emptyMap();
        }
        Map<String, Library> map = new HashMap<>();
        enumerateLibraries(project).forEach(library -> {
            JarCoordinate coordinate = parseCoordinate(library.getName());
            String id = buildLibraryId(coordinate, library);
            if (!id.isBlank() && !map.containsKey(id)) {
                map.put(id, library);
            }
            return true;
        });
        return map;
    }

    private static OrderEnumerator enumerateLibraries(Project project) {
        return ProjectRootManager.getInstance(project)
                .orderEntries()
                .librariesOnly()
                .withoutSdk();
    }

    private static String buildDisplayName(@Nullable JarCoordinate coordinate, Library library) {
        if (coordinate != null) {
            return coordinate.toString();
        }
        String name = Objects.toString(library.getName(), "").trim();
        if (!name.isEmpty()) {
            return name;
        }
        return firstClassRootName(library);
    }

    private static String buildLibraryId(@Nullable JarCoordinate coordinate, Library library) {
        if (coordinate != null) {
            return coordinate.toString();
        }
        String name = Objects.toString(library.getName(), "").trim();
        if (!name.isEmpty()) {
            return name;
        }
        String root = firstClassRootName(library);
        if (!root.isEmpty()) {
            return root;
        }
        return "library-" + Integer.toHexString(System.identityHashCode(library));
    }

    private static String firstClassRootName(Library library) {
        var files = library.getFiles(OrderRootType.CLASSES);
        if (files.length > 0) {
            return files[0].getName();
        }
        return "";
    }

    private static JarCoordinate parseCoordinate(String libraryName) {
        if (libraryName == null) {
            return null;
        }
        Matcher matcher = COORDINATE_PATTERN.matcher(libraryName);
        if (matcher.matches()) {
            return new JarCoordinate(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        String[] parts = libraryName.split(":");
        if (parts.length >= 3) {
            return new JarCoordinate(parts[parts.length - 3], parts[parts.length - 2], parts[parts.length - 1]);
        }
        return null;
    }

    public record JarCoordinate(String groupId, String artifactId, String version) {
        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }

    public record JarInfo(String id, String displayName, @Nullable JarCoordinate coordinate, Library library) {
    }
}

package org.yglue.flow.idea.snapshot;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PlatformUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.yglue.flow.idea.settings.YgflowSettingsState;
import org.yglue.flow.idea.util.JarDependencyResolver;

/**
 * 导出项目 Java 语义结构（源码 + 勾选的 Jar）。
 */
public final class CodeSnapshotExporter {

    private static final Logger LOG = Logger.getInstance(CodeSnapshotExporter.class);

    private CodeSnapshotExporter() {
    }

    public static @NotNull JSONObject buildSnapshot(@NotNull Project project,
                                                    @NotNull YgflowSettingsState settings) {
        JSONObject root = new JSONObject();
        String projectId = settings.projectKey != null && !settings.projectKey.isBlank()
                ? settings.projectKey
                : project.getName();
        root.put("projectId", projectId);
        root.put("projectName", project.getName());
        root.put("snapshotKey", generateSnapshotKey());
        root.put("generatedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        root.put("ide", buildIdeInfo());
        root.put("classes", collectProjectClasses(project));
        root.put("dependencies", collectProjectDependencies(project));
        root.put("selectedJars", new JSONArray(settings.selectedJarCoordinates == null
                ? Set.of()
                : settings.selectedJarCoordinates));
        root.put("jarMetadata", collectJarMetadata(project, settings));
        return root;
    }

    public static @NotNull Path writeSnapshot(@NotNull Project project,
                                              @NotNull JSONObject snapshot) throws IOException {
        String basePath = project.getBasePath();
        if (basePath == null) {
            throw new IllegalStateException("Project basePath is null");
        }
        Path outDir = Path.of(basePath, ".ygflow");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("code-snapshot.json");
        Files.writeString(outFile, snapshot.toString(2));
        return outFile;
    }

    private static @NotNull String generateSnapshotKey() {
        return "snapshot-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static @NotNull JSONObject buildIdeInfo() {
        ApplicationInfo info = ApplicationInfo.getInstance();
        JSONObject ide = new JSONObject();
        ide.put("platform", PlatformUtils.getPlatformPrefix());
        ide.put("productName", info.getFullApplicationName());
        ide.put("version", info.getFullVersion());
        ide.put("build", info.getBuild().asString());
        return ide;
    }

    private static @NotNull JSONArray collectProjectClasses(@NotNull Project project) {
        PsiManager psiManager = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> javaFiles = FilenameIndex.getAllFilesByExt(project, "java", scope);

        Computable<JSONArray> task = () -> {
            JSONArray classes = new JSONArray();
            Set<String> visited = new HashSet<>();
            for (VirtualFile vf : javaFiles) {
                PsiFile psi = psiManager.findFile(vf);
                if (!(psi instanceof PsiJavaFile psiJavaFile)) {
                    continue;
                }
                for (PsiClass psiClass : psiJavaFile.getClasses()) {
                    collectClassRecursive(psiClass, classes, visited);
                }
            }
            return classes;
        };

        return ReadAction.compute(task);
    }

    private static void collectClassRecursive(@NotNull PsiClass psiClass,
                                              @NotNull JSONArray sink,
                                              @NotNull Set<String> visited) {
        if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            return;
        }
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null || qualifiedName.isBlank() || !visited.add(qualifiedName)) {
            return;
        }

        JSONObject clazz = new JSONObject();
        clazz.put("qualifiedName", qualifiedName);
        clazz.put("simpleName", psiClass.getName());
        clazz.put("packageName", psiClass.getContainingFile() instanceof PsiJavaFile javaFile
                ? javaFile.getPackageName() : "");
        clazz.put("kind", determineKind(psiClass));
        clazz.put("fields", collectFields(psiClass));
        clazz.put("methods", collectMethods(psiClass));
        sink.put(clazz);

        for (PsiClass inner : psiClass.getInnerClasses()) {
            collectClassRecursive(inner, sink, visited);
        }
    }

    private static String determineKind(PsiClass psiClass) {
        if (psiClass.isInterface()) {
            return "interface";
        }
        if (psiClass.isEnum()) {
            return "enum";
        }
        return "class";
    }

    private static JSONArray collectFields(PsiClass psiClass) {
        JSONArray fields = new JSONArray();
        for (PsiField field : psiClass.getFields()) {
            if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("name", field.getName());
            item.put("type", renderType(field.getType()));
            item.put("static", field.hasModifierProperty(PsiModifier.STATIC));
            fields.put(item);
        }
        return fields;
    }

    private static JSONArray collectMethods(PsiClass psiClass) {
        JSONArray methods = new JSONArray();
        for (PsiMethod method : psiClass.getMethods()) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("name", method.getName());
            item.put("returnType", renderType(method.getReturnType()));
            item.put("static", method.hasModifierProperty(PsiModifier.STATIC));
            item.put("parameters", collectParameters(method));
            methods.put(item);
        }
        return methods;
    }

    private static JSONArray collectParameters(PsiMethod method) {
        JSONArray params = new JSONArray();
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            JSONObject p = new JSONObject();
            p.put("name", parameter.getName());
            p.put("type", renderType(parameter.getType()));
            params.put(p);
        }
        return params;
    }

    private static String renderType(PsiType type) {
        return type == null ? "void" : type.getCanonicalText();
    }

    private static JSONArray collectProjectDependencies(Project project) {
        JSONArray dependencies = new JSONArray();
        for (JarDependencyResolver.JarInfo info : JarDependencyResolver.listProjectJars(project)) {
            JSONObject dep = new JSONObject();
            dep.put("id", info.id());
            dep.put("name", info.displayName());
            JarDependencyResolver.JarCoordinate coordinate = info.coordinate();
            if (coordinate != null) {
                dep.put("groupId", coordinate.groupId());
                dep.put("artifactId", coordinate.artifactId());
                dep.put("version", coordinate.version());
                dep.put("coordinate", coordinate.toString());
            }
            dependencies.put(dep);
        }
        return dependencies;
    }

    private static JSONArray collectJarMetadata(Project project, YgflowSettingsState settings) {
        JSONArray jars = new JSONArray();
        if (!settings.jarUploadEnabled || settings.selectedJarCoordinates == null || settings.selectedJarCoordinates.isEmpty()) {
            return jars;
        }
        Map<String, Library> libraryMap = JarDependencyResolver.mapLibrariesById(project);
        for (String id : settings.selectedJarCoordinates) {
            Library library = libraryMap.get(id);
            if (library == null) {
                continue;
            }
            try {
                JSONObject jar = buildJarMetadata(library, id);
                if (jar != null) {
                    jars.put(jar);
                }
            } catch (Exception ex) {
                LOG.warn("Failed to build JAR metadata for " + id, ex);
            }
        }
        return jars;
    }

    private static JSONObject buildJarMetadata(Library library, String id)
            throws IOException, NoSuchAlgorithmException {
        JSONArray classes = new JSONArray();
        Set<String> visited = new HashSet<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
            processLibraryRoot(root, classes, visited, digest);
        }
        if (classes.isEmpty()) {
            return null;
        }
        JSONObject jar = new JSONObject();
        jar.put("id", id);
        jar.put("name", Objects.toString(library.getName(), id));
        jar.put("classes", classes);
        jar.put("contentHash", toHex(digest.digest()));
        return jar;
    }

    private static void processLibraryRoot(VirtualFile file,
                                           JSONArray sink,
                                            Set<String> visited,
                                            MessageDigest digest) throws IOException {
        if (file == null) {
            return;
        }
        if (file.isDirectory()) {
            for (VirtualFile child : file.getChildren()) {
                processLibraryRoot(child, sink, visited, digest);
            }
            return;
        }
        if (!"class".equalsIgnoreCase(file.getExtension())) {
            return;
        }
        byte[] bytes;
        try (InputStream in = file.getInputStream()) {
            bytes = in.readAllBytes();
        }
        digest.update(bytes);
        addJarClass(bytes, sink, visited);
    }

    private static void addJarClass(byte[] bytes, JSONArray sink, Set<String> visited) {
        try {
            ClassReader reader = new ClassReader(bytes);
            JarClassVisitor visitor = new JarClassVisitor();
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (!visitor.isPublicClass()) {
                return;
            }
            JSONObject clazz = visitor.toJson();
            String name = clazz.optString("qualifiedName");
            if (!name.isBlank() && visited.add(name)) {
                sink.put(clazz);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to parse class entry from jar", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static final class JarClassVisitor extends ClassVisitor {
        private final JSONObject clazz = new JSONObject();
        private final JSONArray fields = new JSONArray();
        private final JSONArray methods = new JSONArray();
        private boolean publicClass;

        JarClassVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.publicClass = (access & Opcodes.ACC_PUBLIC) != 0;
            String qualifiedName = name.replace('/', '.');
            clazz.put("qualifiedName", qualifiedName);
            int slash = name.lastIndexOf('/');
            if (slash >= 0) {
                clazz.put("simpleName", name.substring(slash + 1));
                clazz.put("packageName", name.substring(0, slash).replace('/', '.'));
            } else {
                clazz.put("simpleName", qualifiedName);
                clazz.put("packageName", "");
            }
            clazz.put("kind", determineKind(access));
            clazz.put("fields", fields);
            clazz.put("methods", methods);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (!isPublic(access)) {
                return null;
            }
            JSONObject field = new JSONObject();
            field.put("name", name);
            field.put("type", Type.getType(descriptor).getClassName());
            field.put("static", (access & Opcodes.ACC_STATIC) != 0);
            fields.put(field);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (!isPublic(access)) {
                return null;
            }
            JSONObject method = new JSONObject();
            method.put("name", name);
            method.put("returnType", Type.getReturnType(descriptor).getClassName());
            method.put("static", (access & Opcodes.ACC_STATIC) != 0);
            method.put("parameters", renderParameters(descriptor));
            methods.put(method);
            return null;
        }

        private JSONArray renderParameters(String descriptor) {
            JSONArray params = new JSONArray();
            for (Type arg : Type.getArgumentTypes(descriptor)) {
                JSONObject p = new JSONObject();
                p.put("type", arg.getClassName());
                params.put(p);
            }
            return params;
        }

        private boolean isPublic(int access) {
            return (access & Opcodes.ACC_PUBLIC) != 0 && (access & Opcodes.ACC_SYNTHETIC) == 0;
        }

        private String determineKind(int access) {
            if ((access & Opcodes.ACC_INTERFACE) != 0) {
                return "interface";
            }
            if ((access & Opcodes.ACC_ENUM) != 0) {
                return "enum";
            }
            return "class";
        }

        boolean isPublicClass() {
            return publicClass;
        }

        JSONObject toJson() {
            return clazz;
        }
    }
}

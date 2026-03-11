package org.yglue.flow.runtime.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.FlowLoader;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlowToJavaGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    public void testGenerate() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File(
                "d:\\JavaWorkspace\\ygflow-suite\\samples\\yglue-sample-service\\src\\main\\resources\\ygflow\\rules\\sample_math_flow.json");
        FlowDefinition flow = new FlowLoader(mapper)
                .parse("sample_math_flow", mapper.readTree(file));

        FlowToJavaGenerator generator = new FlowToJavaGenerator();
        String code = generator.generate(flow, "org.example.generated", "Flow_SampleMath");

        assertTrue(code.contains("package org.example.generated;"));
        assertTrue(code.contains("public class Flow_SampleMath"));
        assertFalse(code.contains("@Autowired"));
        assertFalse(code.contains("@Component"));
        assertFalse(code.contains("java.util.function.Supplier<"));
        assertFalse(code.contains("ctx.put("));
        assertTrue(code.contains("ctx.set(\"ret1a863cfc29a2573aea10e13ebda406bc\", result);"));
        assertTrue(code.contains("private final org.yglue.flow.sample.TestService testService;"));
        assertTrue(code.contains("java.lang.Object arg1 = null; // unsupported inline script: {'age':'20'}"));

        compileGeneratedSources(code);
    }

    private void compileGeneratedSources(String generatedSource) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required for this test");

        Path srcRoot = tempDir.resolve("src");
        Path outRoot = tempDir.resolve("classes");
        Files.createDirectories(srcRoot);
        Files.createDirectories(outRoot);

        Path generatedFile = writeSource(srcRoot, "org.example.generated", "Flow_SampleMath", generatedSource);
        Path serviceFile = writeSource(srcRoot, "org.yglue.flow.sample", "TestService", """
                package org.yglue.flow.sample;

                public class TestService {
                    public String testB(String a, Object userDTO) {
                        return a + ":" + userDTO;
                    }
                }
                """);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable compilationUnits = fileManager.getJavaFileObjects(
                    generatedFile.toFile(),
                    serviceFile.toFile());

            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", outRoot.toString());

            Boolean success = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
            assertTrue(Boolean.TRUE.equals(success), "Generated Java source should compile");
        }
    }

    private Path writeSource(Path root, String packageName, String className, String source) throws IOException {
        Path dir = root.resolve(packageName.replace('.', File.separatorChar));
        Files.createDirectories(dir);
        Path file = dir.resolve(className + ".java");
        Files.writeString(file, source);
        return file;
    }
}

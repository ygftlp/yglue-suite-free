package org.yglue.flow.runtime.core.script;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groovy 脚本执行器（带编译缓存）。
 * <p>
 * 使用脚本文本的 SHA-256 作为缓存 key，避免每次执行重复编译。
 */
public final class GroovyScriptEngine {

    private static final GroovyScriptEngine SHARED = new GroovyScriptEngine();

    private static final CompilerConfiguration COMPILER_CONFIGURATION = new CompilerConfiguration();
    private static final GroovyClassLoader CLASS_LOADER =
            new GroovyClassLoader(GroovyScriptEngine.class.getClassLoader(), COMPILER_CONFIGURATION);

    private final ConcurrentHashMap<String, Class<? extends Script>> compiledScriptCache = new ConcurrentHashMap<>();

    private GroovyScriptEngine() {
    }

    public static GroovyScriptEngine shared() {
        return SHARED;
    }

    /**
     * 执行脚本。
     *
     * @param script    Groovy 脚本文本
     * @param variables 绑定变量
     * @return 执行结果
     */
    public Object evaluate(String script, Map<String, Object> variables) {
        if (script == null || script.isBlank()) {
            return null;
        }
        Class<? extends Script> scriptClass = compile(script);
        try {
            Script scriptInstance = scriptClass.getDeclaredConstructor().newInstance();
            Binding binding = new Binding();
            if (variables != null && !variables.isEmpty()) {
                variables.forEach(binding::setVariable);
            }
            scriptInstance.setBinding(binding);
            return scriptInstance.run();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to execute Groovy script", ex);
        }
    }

    public int cacheSize() {
        return compiledScriptCache.size();
    }

    public void clearCache() {
        compiledScriptCache.clear();
    }

    private Class<? extends Script> compile(String script) {
        String checksum = sha256(script);
        return compiledScriptCache.computeIfAbsent(checksum, key -> {
            Class<?> compiled = CLASS_LOADER.parseClass(script, "ygflow_" + key + ".groovy");
            if (!Script.class.isAssignableFrom(compiled)) {
                throw new IllegalStateException("Compiled Groovy class is not a Script: " + compiled.getName());
            }
            @SuppressWarnings("unchecked")
            Class<? extends Script> scriptClass = (Class<? extends Script>) compiled;
            return scriptClass;
        });
    }

    private String sha256(String script) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(script.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}

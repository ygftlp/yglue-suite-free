package org.yglue.flow.runtime.core.expression;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 表达式引擎注册与访问入口，便于全局配置默认实现。
 */
public final class ExpressionEngines {

    private static final AtomicReference<ExpressionEngine> DEFAULT = new AtomicReference<>();

    private ExpressionEngines() {
    }

    /**
     * 设置默认的表达式引擎。
     */
    public static void setDefault(ExpressionEngine engine) {
        DEFAULT.set(Objects.requireNonNull(engine, "engine must not be null"));
    }

    /**
     * 获取默认表达式引擎，若尚未显式设置则懒加载 SpEL 实现。
     */
    public static ExpressionEngine getDefault() {
        ExpressionEngine engine = DEFAULT.get();
        if (engine == null) {
            ExpressionEngine newEngine = new SpelExpressionEngine();
            if (DEFAULT.compareAndSet(null, newEngine)) {
                engine = newEngine;
            } else {
                engine = DEFAULT.get();
            }
        }
        return engine;
    }
}





package org.yglue.flow.runtime.core.engine.interceptors;

import java.util.Map;

/**
 * 轻量级的 JsonPath 提取工具
 * 替代之前的 Groovy 引擎对于 `ctx.request.body.xxx` 等简单路径的提取。
 * 实现零编译、极速的属性访问。
 */
public class JsonPathUtil {

    private JsonPathUtil() {
    }

    /**
     * 根据点分路径提取对象树中的值
     *
     * @param source 数据源 (通常是 FlowContext 的 variables)
     * @param path   路径，例如 request.body.user.age
     * @return 提取到的值，如果没有则返回 null
     */
    @SuppressWarnings("unchecked")
    public static Object extract(Map<String, Object> source, String path) {
        if (source == null || path == null || path.isBlank()) {
            return source;
        }

        // 兼容 V1 的 "ctx." 前缀：如果以 ctx. 开头，则去掉，因为我们传入的已经是 ctx 的内部变量
        if (path.startsWith("ctx.")) {
            path = path.substring(4);
        }

        String[] parts = path.split("\\.");
        Object current = source;

        for (String part : parts) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                // 如果遇到非 Map 的复杂对象，可在此处扩展反射提取，
                // 但当前框架规定了 Context 内主要为 Map(通过 Jackson 转换)。
                try {
                    // 退化兜底：反射尝试获取该字段
                    java.lang.reflect.Field field = current.getClass().getDeclaredField(part);
                    field.setAccessible(true);
                    current = field.get(current);
                } catch (Exception e) {
                    return null; // 无法提取
                }
            }
        }
        return current;
    }
}

package org.yglue.flow.runtime.core.util;

import org.yglue.flow.runtime.core.engine.FlowContext;

import java.util.Map;

/**
 * 参数解析器工具类
 * 用于解析节点输入参数，支持从流程上下文或请求参数中获取�?
 */
public final class ParamResolver {

    private ParamResolver() {
    }

    /**
     * 解析参数�?
     * 
     * @param resolverConfig 解析器配置，格式�?
     *   {
     *     "type": "request" | "context" | "constant" | "expression",
     *     "path": "request.path.projectKey" (�?type �?request �?context �?,
     *     "constant": "常量�? (�?type �?constant �?,
     *     "expression": "#{ctx.price * 0.9}" (�?type �?expression �?,
     *     "default": "默认�?
     *   }
     * @param context 流程上下�?
     * @return 解析后的参数�?
     */
    @SuppressWarnings("unchecked")
    public static Object resolve(Object resolverConfig, FlowContext context) {
        if (resolverConfig == null) {
            return null;
        }

        if (!(resolverConfig instanceof Map)) {
            // 如果不是 Map，尝试作为表达式评估
            return ExpressionEvaluator.evaluate(resolverConfig, context);
        }

        Map<String, Object> config = (Map<String, Object>) resolverConfig;
        String type = getString(config, "type", "request");
        Object defaultValue = config.get("default");

        Object value = null;

        switch (type) {
            case "request":
            case "context":
                // 从路径中获取�?
                String path = getString(config, "path", "");
                if (!path.isEmpty()) {
                    value = resolvePath(path, context);
                }
                break;

            case "constant":
                // 常量�?
                value = config.get("constant");
                break;

            case "expression":
                // SpEL 表达�?
                String expression = getString(config, "expression", "");
                if (!expression.isEmpty()) {
                    value = ExpressionEvaluator.evaluate(expression, context);
                }
                break;

            default:
                // 默认尝试作为表达式评�?
                value = ExpressionEvaluator.evaluate(resolverConfig, context);
                break;
        }

        // 如果值为空，使用默认�?
        if (value == null && defaultValue != null) {
            if (defaultValue instanceof String && !((String) defaultValue).isEmpty()) {
                // 默认值可能是字符串，尝试作为表达式评�?
                value = ExpressionEvaluator.evaluate(defaultValue, context);
            } else {
                value = defaultValue;
            }
        }

        return value;
    }

    /**
     * 解析路径值（支持点号分隔的嵌套路径，�?"request.path.projectKey"�?
     */
    @SuppressWarnings("unchecked")
    private static Object resolvePath(String path, FlowContext context) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // 如果路径�?SpEL 表达式，直接评估
        if (path.startsWith("#{") && path.endsWith("}")) {
            return ExpressionEvaluator.evaluate(path, context);
        }

        // 解析点号分隔的路�?
        String[] parts = path.split("\\.");
        if (parts.length == 0) {
            return null;
        }

        Map<String, Object> data = context.data();
        Object current = data;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                // 尝试使用反射获取属�?
                try {
                    java.lang.reflect.Field field = current.getClass().getDeclaredField(part);
                    field.setAccessible(true);
                    current = field.get(current);
                } catch (Exception e) {
                    return null;
                }
            }
        }

        return current;
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }
}





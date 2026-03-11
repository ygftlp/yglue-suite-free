package org.yglue.flow.runtime.core.util;

import org.yglue.flow.runtime.core.engine.FlowContext;

import java.util.Map;

/**
 * Utility helpers for resolving legacy parameter config objects.
 */
public final class ParamResolver {

    private ParamResolver() {
    }

    /**
     * Resolves a value from a legacy resolver config.
     */
    @SuppressWarnings("unchecked")
    public static Object resolve(Object resolverConfig, FlowContext context) {
        if (resolverConfig == null) {
            return null;
        }

        if (!(resolverConfig instanceof Map)) {
            return ExpressionEvaluator.evaluate(resolverConfig, context);
        }

        Map<String, Object> config = (Map<String, Object>) resolverConfig;
        String type = getString(config, "type", "request");
        Object defaultValue = config.get("default");

        Object value = null;

        switch (type) {
            case "request":
            case "context":
                String path = getString(config, "path", "");
                if (!path.isEmpty()) {
                    value = resolvePath(path, context);
                }
                break;

            case "constant":
                value = config.get("constant");
                break;

            case "expression":
                String expression = getString(config, "expression", "");
                if (!expression.isEmpty()) {
                    value = ExpressionEvaluator.evaluate(expression, context);
                }
                break;

            default:
                value = ExpressionEvaluator.evaluate(resolverConfig, context);
                break;
        }

        if (value == null && defaultValue != null) {
            if (defaultValue instanceof String && !((String) defaultValue).isEmpty()) {
                value = ExpressionEvaluator.evaluate(defaultValue, context);
            } else {
                value = defaultValue;
            }
        }

        return value;
    }

    /**
     * Resolves dotted property paths like {@code request.path.projectKey}.
     */
    @SuppressWarnings("unchecked")
    private static Object resolvePath(String path, FlowContext context) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        if (path.startsWith("#{") && path.endsWith("}")) {
            return ExpressionEvaluator.evaluate(path, context);
        }

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
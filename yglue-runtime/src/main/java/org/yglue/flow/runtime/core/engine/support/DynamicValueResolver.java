package org.yglue.flow.runtime.core.engine.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.engine.interceptors.JsonPathUtil;
import org.yglue.flow.runtime.core.expression.ExpressionEngines;
import org.yglue.flow.runtime.core.expression.ExpressionEvaluationContext;
import org.yglue.flow.runtime.core.util.ApplicationContextProvider;
import org.yglue.flow.runtime.rest.HttpRestInvocationStrategy;
import org.yglue.flow.runtime.rest.RestInvocationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DynamicValueResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DynamicValueResolver() {
    }

    @SuppressWarnings("unchecked")
    public static Object resolveSource(Map<String, Object> source,
            FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        if (source == null) {
            return null;
        }

        String kind = text(source.getOrDefault("kind", "const"));
        return switch (kind) {
            case "ctx" -> JsonPathUtil.extract(context.getVariables(), text(source.get("path")));
            case "tempVar" -> JsonPathUtil.extract(tempVars, text(source.get("tempKey")));
            case "const" -> source.get("constValue");
            case "expr", "expression" -> evaluateExpression(source, context, tempVars);
            case "serviceCall" -> {
                Object rawServiceCall = source.get("serviceCall");
                Map<String, Object> serviceCall = rawServiceCall instanceof Map<?, ?>
                        ? (Map<String, Object>) rawServiceCall
                        : Map.of();
                Object result = invokeServiceCall(serviceCall, context, tempVars, resolveApplicationContext(applicationContext));
                yield extractResultPath(result, firstNonBlank(
                        text(source.get("serviceResultPath")),
                        text(source.get("resultPath"))));
            }
            case "httpCall" -> {
                Object rawHttpCall = source.get("httpCall");
                Map<String, Object> httpCall = rawHttpCall instanceof Map<?, ?>
                        ? (Map<String, Object>) rawHttpCall
                        : Map.of();
                yield invokeHttpCall(httpCall, context, tempVars, resolveApplicationContext(applicationContext));
            }
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    public static Object invokeServiceCall(Map<String, Object> serviceCall,
            FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        if (serviceCall == null || serviceCall.isEmpty()) {
            return null;
        }

        Map<String, Object> serviceRef = serviceCall.get("serviceRef") instanceof Map<?, ?> rawServiceRef
                ? (Map<String, Object>) rawServiceRef
                : Map.of();

        String beanName = text(serviceRef.get("serviceBean"));
        String methodName = text(serviceRef.get("methodName"));
        String methodSignature = text(serviceRef.get("methodSignature"));

        if (beanName.isBlank() || methodName.isBlank()) {
            String fn = text(serviceCall.get("fn"));
            int separatorIndex = fn.lastIndexOf('.');
            if (separatorIndex > 0) {
                if (beanName.isBlank()) {
                    beanName = fn.substring(0, separatorIndex).trim();
                }
                if (methodName.isBlank()) {
                    methodName = fn.substring(separatorIndex + 1).trim();
                }
            }
        }

        if (beanName.isBlank() || methodName.isBlank()) {
            throw new IllegalArgumentException("serviceCall requires serviceRef.serviceBean and serviceRef.methodName");
        }

        ApplicationContext appContext = resolveApplicationContext(applicationContext);
        if (appContext == null) {
            throw new IllegalStateException("serviceCall requires Spring ApplicationContext");
        }

        Object bean = appContext.getBean(beanName);
        Class<?> targetClass = appContext.getType(beanName);
        if (targetClass == null) {
            targetClass = AopProxyUtils.ultimateTargetClass(bean);
        }

        List<Object> args = new ArrayList<>();
        Object rawBindings = serviceCall.get("argBindings");
        if (rawBindings instanceof List<?> bindings) {
            for (Object item : bindings) {
                if (!(item instanceof Map<?, ?> rawBinding)) {
                    args.add(null);
                    continue;
                }
                args.add(resolveArgumentBinding((Map<String, Object>) rawBinding, context, tempVars, applicationContext));
            }
        }

        Method method = resolveMethod(targetClass, methodName, methodSignature, args.size());
        Object[] convertedArgs = convertArgs(method.getParameterTypes(), args);
        try {
            return method.invoke(bean, convertedArgs);
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Failed to invoke serviceCall: " + beanName + "." + methodName, ex);
        }
    }

    public static Object invokeHttpCall(Map<String, Object> httpCall,
            FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        if (httpCall == null || httpCall.isEmpty()) {
            return null;
        }

        Map<String, Object> normalizedConfig = normalizeHttpCallConfig(httpCall, context, tempVars);
        HttpRestInvocationStrategy strategy = new HttpRestInvocationStrategy();
        RestInvocationContext invocationContext = new RestInvocationContext(
                null,
                context,
                normalizedConfig,
                applicationContext);
        try {
            Object result = strategy.invoke(invocationContext);
            return extractResultPath(result, text(httpCall.get("resultPath")));
        } catch (Exception ex) {
            throw new RuntimeException("httpCall failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object resolveArgumentBinding(Map<String, Object> binding,
            FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        Map<String, Object> source = binding.get("source") instanceof Map<?, ?> rawSource
                ? (Map<String, Object>) rawSource
                : Map.of();
        String mode = text(source.get("mode"));
        if ("objectBuilder".equalsIgnoreCase(mode)) {
            Map<String, Object> result = new LinkedHashMap<>();
            Object rawFields = source.get("objectFields");
            if (rawFields instanceof List<?> objectFields) {
                for (Object item : objectFields) {
                    if (!(item instanceof Map<?, ?> rawField)) {
                        continue;
                    }
                    Map<String, Object> field = (Map<String, Object>) rawField;
                    String fieldPath = text(field.get("fieldPath"));
                    if (fieldPath.isBlank()) {
                        continue;
                    }
                    Map<String, Object> fieldSource = field.get("source") instanceof Map<?, ?> childSource
                            ? (Map<String, Object>) childSource
                            : Map.of();
                    deepPut(result, fieldPath, resolveSource(fieldSource, context, tempVars, applicationContext));
                }
            }
            return result;
        }
        return resolveSource(source, context, tempVars, applicationContext);
    }

    private static Object evaluateExpression(Map<String, Object> source,
            FlowContext context,
            Map<String, Object> tempVars) {
        String expression = firstNonBlank(
                text(source.get("value")),
                text(source.get("expression")));
        if (expression.isBlank()) {
            return null;
        }

        ExpressionEvaluationContext evaluationContext = ExpressionEvaluationContext.builder()
                .variables(context.getVariables())
                .variable("ctx", context.getVariables())
                .variable("tempVars", tempVars)
                .variable("__tmp", tempVars)
                .attribute("flowContext", context)
                .build();
        return ExpressionEngines.getDefault().evaluate(expression, evaluationContext);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeHttpCallConfig(Map<String, Object> httpCall,
            FlowContext context,
            Map<String, Object> tempVars) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("method", firstNonBlank(text(httpCall.get("method")), "GET"));
        normalized.put("url", text(httpCall.get("url")));

        List<Map<String, Object>> headers = normalizeHttpBindings(httpCall.get("headers"), context, tempVars);
        if (!headers.isEmpty()) {
            normalized.put("headers", headers);
        }

        List<Map<String, Object>> query = normalizeHttpBindings(httpCall.get("query"), context, tempVars);
        if (!query.isEmpty()) {
            normalized.put("query", query);
        }

        String bodyJson = text(httpCall.get("bodyJson"));
        if (!bodyJson.isBlank()) {
            normalized.put("body", parseJsonLike(bodyJson));
        }

        copyIfPresent(httpCall, normalized, "timeoutSeconds");
        copyIfPresent(httpCall, normalized, "retryCount");
        copyIfPresent(httpCall, normalized, "retryBackoffMs");
        copyIfPresent(httpCall, normalized, "retryOnStatuses");
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> normalizeHttpBindings(Object rawBindings,
            FlowContext context,
            Map<String, Object> tempVars) {
        if (!(rawBindings instanceof List<?> bindings)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : bindings) {
            if (!(item instanceof Map<?, ?> rawBinding)) {
                continue;
            }
            Map<String, Object> binding = (Map<String, Object>) rawBinding;
            String key = text(binding.get("key"));
            if (key.isBlank()) {
                continue;
            }
            String valueKind = text(binding.get("valueKind"));
            Map<String, Object> valueSource = new LinkedHashMap<>();
            switch (valueKind) {
                case "ctx" -> {
                    valueSource.put("kind", "ctx");
                    valueSource.put("path", text(binding.get("valuePath")));
                }
                case "tempVar" -> {
                    valueSource.put("kind", "tempVar");
                    valueSource.put("tempKey", text(binding.get("tempKey")));
                }
                default -> {
                    valueSource.put("kind", "const");
                    valueSource.put("constValue", binding.get("valueConst"));
                }
            }
            Object value = resolveSource(valueSource, context, tempVars, null);
            if (value == null) {
                continue;
            }
            Map<String, Object> normalizedItem = new LinkedHashMap<>();
            normalizedItem.put("key", key);
            normalizedItem.put("value", value);
            result.add(normalizedItem);
        }
        return result;
    }

    private static Method resolveMethod(Class<?> targetClass,
            String methodName,
            String methodSignature,
            int argCount) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : targetClass.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == argCount) {
                candidates.add(method);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Method not found: " + targetClass.getName() + "." + methodName + "/" + argCount);
        }
        if (!methodSignature.isBlank()) {
            List<String> signatureTypes = parseSignatureParamTypes(methodSignature);
            for (Method method : candidates) {
                if (matchesSignature(method, signatureTypes)) {
                    return method;
                }
            }
        }
        return candidates.get(0);
    }

    private static boolean matchesSignature(Method method, List<String> signatureTypes) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != signatureTypes.size()) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index += 1) {
            String expected = normalizeTypeName(signatureTypes.get(index));
            String actual = normalizeTypeName(parameterTypes[index].getName());
            if (!expected.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> parseSignatureParamTypes(String methodSignature) {
        int start = methodSignature.indexOf('(');
        int end = methodSignature.lastIndexOf(')');
        if (start < 0 || end <= start) {
            return List.of();
        }
        String body = methodSignature.substring(start + 1, end).trim();
        if (body.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int genericDepth = 0;
        StringBuilder token = new StringBuilder();
        for (int index = 0; index < body.length(); index += 1) {
            char current = body.charAt(index);
            if (current == '<') {
                genericDepth += 1;
            } else if (current == '>') {
                genericDepth = Math.max(0, genericDepth - 1);
            } else if (current == ',' && genericDepth == 0) {
                result.add(token.toString().trim());
                token.setLength(0);
                continue;
            }
            token.append(current);
        }
        if (token.length() > 0) {
            result.add(token.toString().trim());
        }
        return result;
    }

    private static String normalizeTypeName(String typeName) {
        String normalized = typeName == null ? "" : typeName.trim();
        return switch (normalized) {
            case "int" -> Integer.class.getName();
            case "long" -> Long.class.getName();
            case "double" -> Double.class.getName();
            case "float" -> Float.class.getName();
            case "short" -> Short.class.getName();
            case "byte" -> Byte.class.getName();
            case "boolean" -> Boolean.class.getName();
            case "char" -> Character.class.getName();
            default -> normalized;
        };
    }

    private static Object[] convertArgs(Class<?>[] parameterTypes, List<Object> args) {
        Object[] converted = new Object[parameterTypes.length];
        for (int index = 0; index < parameterTypes.length; index += 1) {
            Object value = index < args.size() ? args.get(index) : null;
            converted[index] = convertValue(value, parameterTypes[index]);
        }
        return converted;
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(String.valueOf(value));
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(String.valueOf(value));
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(String.valueOf(value));
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(String.valueOf(value));
        }
        if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(String.valueOf(value));
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(String.valueOf(value));
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        if (targetType == char.class || targetType == Character.class) {
            String text = String.valueOf(value);
            return text.isEmpty() ? null : text.charAt(0);
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (!trimmed.isEmpty() && (trimmed.startsWith("{") || trimmed.startsWith("["))) {
                try {
                    Object parsed = OBJECT_MAPPER.readValue(trimmed, Object.class);
                    return OBJECT_MAPPER.convertValue(parsed, targetType);
                } catch (Exception ignored) {
                }
            }
        }
        return OBJECT_MAPPER.convertValue(value, targetType);
    }

    @SuppressWarnings("unchecked")
    private static void deepPut(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int index = 0; index < parts.length - 1; index += 1) {
            String part = parts[index];
            Object child = current.get(part);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                current.put(part, child);
            }
            current = (Map<String, Object>) child;
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private static Object extractResultPath(Object result, String resultPath) {
        if (result == null || resultPath.isBlank()) {
            return result;
        }
        String normalizedPath = resultPath.startsWith("$.") ? resultPath.substring(2) : resultPath;
        if (result instanceof Map<?, ?> rawMap) {
            return JsonPathUtil.extract((Map<String, Object>) rawMap, normalizedPath);
        }
        try {
            Map<String, Object> asMap = OBJECT_MAPPER.convertValue(result, Map.class);
            return JsonPathUtil.extract(asMap, normalizedPath);
        } catch (IllegalArgumentException ex) {
            return result;
        }
    }

    private static Object parseJsonLike(String bodyJson) {
        String trimmed = bodyJson == null ? "" : bodyJson.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(trimmed, Object.class);
        } catch (Exception ignored) {
            return bodyJson;
        }
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private static ApplicationContext resolveApplicationContext(ApplicationContext applicationContext) {
        if (applicationContext != null) {
            return applicationContext;
        }
        return ApplicationContextProvider.getApplicationContext();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}

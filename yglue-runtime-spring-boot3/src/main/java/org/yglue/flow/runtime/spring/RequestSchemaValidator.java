package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.yglue.flow.runtime.core.definition.RestEntryPoint;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 根据入口 Schema 对传入请求进行校验，并生成规范化的参数。
 */
class RequestSchemaValidator {

    private static final String ERROR_CODE = "FLOW_BAD_REQUEST";
    private static final String DEFAULT_MESSAGE = "Request validation failed";

    private final ObjectMapper objectMapper;

    RequestSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ValidationResult validate(RestEntryPoint entryPoint,
                              HttpServletRequest request,
                              Map<String, Object> payload) {
        JsonNode schemaNode = resolveSchema(entryPoint);
        if (schemaNode == null || schemaNode.isMissingNode()) {
            return ValidationResult.success(null);
        }
        if (!"object".equals(schemaNode.path("type").asText())) {
            return ValidationResult.success(null);
        }
        ValidationContext context = new ValidationContext(request, payload, objectMapper);
        Map<String, Object> normalized = new LinkedHashMap<>();
        List<FieldError> errors = new ArrayList<>();
        validateObject(schemaNode, context, context.bodyAsObject(), "", normalized, errors, true);
        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success(normalized);
    }

    private JsonNode resolveSchema(RestEntryPoint entryPoint) {
        if (entryPoint == null) {
            return null;
        }
        JsonNode schema = entryPoint.getRequestSchema();
        if (schema != null && !schema.isMissingNode()) {
            return schema;
        }
        String raw = entryPoint.getRequestSchemaJson();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (IOException ex) {
            return null;
        }
    }

    private void validateObject(JsonNode schema,
                                ValidationContext context,
                                Object currentValue,
                                String path,
                                Map<String, Object> target,
                                List<FieldError> errors,
                                boolean topLevel) {
        Map<String, Object> currentMap = context.toMap(currentValue);
        JsonNode properties = schema.path("properties");
        Set<String> required = readRequiredNames(schema);
        Iterator<String> names = properties.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode childSchema = properties.get(name);
            boolean requiredField = required.contains(name) || childSchema.path("x-required").asBoolean(false);
            String childPath = buildPath(path, name);
            Object value = resolveValue(name, childSchema, context, currentMap, topLevel);
            if (value == null) {
                if (childSchema.has("default") && !childSchema.get("default").isNull()) {
                    value = readDefault(childSchema.get("default"));
                }
            }
            if (value == null) {
                if (requiredField) {
                    errors.add(new FieldError(childPath, "必填"));
                }
                continue;
            }
            Object validated = validateByType(childSchema, value, context, childPath, errors, requiredField);
            if (validated != null || requiredField) {
                target.put(name, validated);
            }
        }
    }

    private Object validateByType(JsonNode schema,
                                  Object raw,
                                  ValidationContext context,
                                  String path,
                                  List<FieldError> errors,
                                  boolean requiredField) {
        String type = schema.path("type").asText();
        switch (type) {
            case "object" -> {
                Map<String, Object> container = new LinkedHashMap<>();
                validateObject(schema, context, raw, path, container, errors, false);
                return container;
            }
            case "array" -> {
                List<Object> list = context.toList(raw);
                if (list == null) {
                    errors.add(new FieldError(path, "应为数组"));
                    return null;
                }
                Integer minItems = readInt(schema, "minItems");
                Integer maxItems = readInt(schema, "maxItems");
                if (minItems != null && list.size() < minItems) {
                    errors.add(new FieldError(path, "至少需要 " + minItems + " 项"));
                }
                if (maxItems != null && list.size() > maxItems) {
                    errors.add(new FieldError(path, "最多允许 " + maxItems + " 项"));
                }
                JsonNode itemSchema = schema.path("items");
                if (!itemSchema.isMissingNode()) {
                    List<Object> normalized = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) {
                        Object element = list.get(i);
                        String childPath = path + "[" + i + "]";
                        Object normalizedElement = validateByType(itemSchema, element, context, childPath, errors, requiredField);
                        normalized.add(normalizedElement);
                    }
                    return normalized;
                }
                return list;
            }
            case "integer" -> {
                BigDecimal number = toBigDecimal(raw, path, errors);
                if (number == null) {
                    return null;
                }
                if (number.stripTrailingZeros().scale() > 0) {
                    errors.add(new FieldError(path, "必须为整数"));
                    return null;
                }
                checkRange(schema, number, path, errors);
                return number.longValue();
            }
            case "number" -> {
                BigDecimal number = toBigDecimal(raw, path, errors);
                if (number == null) {
                    return null;
                }
                checkRange(schema, number, path, errors);
                return number;
            }
            case "boolean" -> {
                Boolean bool = toBoolean(raw);
                if (bool == null) {
                    errors.add(new FieldError(path, "必须为布尔值"));
                    return null;
                }
                return bool;
            }
            case "string" -> {
                String text = raw.toString();
                Integer minLength = readInt(schema, "minLength");
                Integer maxLength = readInt(schema, "maxLength");
                if (minLength != null && text.length() < minLength) {
                    errors.add(new FieldError(path, "长度至少为 " + minLength));
                }
                if (maxLength != null && text.length() > maxLength) {
                    errors.add(new FieldError(path, "长度不能超过 " + maxLength));
                }
                if (schema.has("pattern")) {
                    String pattern = schema.get("pattern").asText();
                    if (!Pattern.compile(pattern).matcher(text).matches()) {
                        errors.add(new FieldError(path, "格式不合法"));
                    }
                }
                if (schema.has("enum") && schema.get("enum").isArray()) {
                    boolean matched = false;
                    for (JsonNode node : schema.get("enum")) {
                        if (Objects.equals(node.asText(), text)) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        errors.add(new FieldError(path, "不在允许的取值范围内"));
                    }
                }
                return text;
            }
            default -> {
                return raw;
            }
        }
    }

    private void checkRange(JsonNode schema,
                            BigDecimal number,
                            String path,
                            List<FieldError> errors) {
        BigDecimal minimum = readDecimal(schema, "minimum");
        BigDecimal maximum = readDecimal(schema, "maximum");
        if (minimum != null && number.compareTo(minimum) < 0) {
            errors.add(new FieldError(path, "数值不能小于 " + minimum));
        }
        if (maximum != null && number.compareTo(maximum) > 0) {
            errors.add(new FieldError(path, "数值不能大于 " + maximum));
        }
    }

    private Object resolveValue(String fieldName,
                                JsonNode schema,
                                ValidationContext context,
                                Map<String, Object> currentMap,
                                boolean topLevel) {
        String source = schema.path("x-source").asText(null);
        if (source != null && !source.isBlank()) {
            return switch (source) {
                case "path" -> context.lookupPath(schema, fieldName);
                case "query" -> context.lookupQuery(schema, fieldName);
                case "header" -> context.lookupHeader(schema, fieldName);
                case "form" -> context.lookupForm(schema, fieldName);
                case "body" -> context.lookupBody(schema, fieldName, currentMap, topLevel);
                default -> context.lookupBody(schema, fieldName, currentMap, topLevel);
            };
        }
        if (currentMap != null) {
            return currentMap.get(fieldName);
        }
        return context.lookupBody(schema, fieldName, currentMap, topLevel);
    }

    private Set<String> readRequiredNames(JsonNode schema) {
        Set<String> required = new HashSet<>();
        JsonNode requiredNode = schema.path("required");
        if (requiredNode.isArray()) {
            requiredNode.forEach(node -> required.add(node.asText()));
        }
        return required;
    }

    private Integer readInt(JsonNode schema, String field) {
        JsonNode node = schema.get(field);
        if (node == null || !node.isNumber()) {
            return null;
        }
        return node.intValue();
    }

    private BigDecimal readDecimal(JsonNode schema, String field) {
        JsonNode node = schema.get(field);
        if (node == null) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Object readDefault(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.getNodeType() == JsonNodeType.OBJECT || node.getNodeType() == JsonNodeType.ARRAY) {
            return objectMapper.convertValue(node, Object.class);
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private String buildPath(String current, String next) {
        if (current == null || current.isBlank()) {
            return next;
        }
        return current + "." + next;
    }

    private BigDecimal toBigDecimal(Object raw, String path, List<FieldError> errors) {
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (raw instanceof String str) {
            try {
                return new BigDecimal(str.trim());
            } catch (NumberFormatException ex) {
                errors.add(new FieldError(path, "数值格式不正确"));
                return null;
            }
        }
        errors.add(new FieldError(path, "必须为数值"));
        return null;
    }

    private Boolean toBoolean(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof String str) {
            String normalized = str.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        return null;
    }

    static class ValidationResult {
        private final boolean valid;
        private final Map<String, Object> normalized;
        private final List<FieldError> errors;

        private ValidationResult(boolean valid,
                                 Map<String, Object> normalized,
                                 List<FieldError> errors) {
            this.valid = valid;
            this.normalized = normalized;
            this.errors = errors;
        }

        static ValidationResult success(Map<String, Object> normalized) {
            return new ValidationResult(true, normalized, List.of());
        }

        static ValidationResult failure(List<FieldError> errors) {
            return new ValidationResult(false, null, List.copyOf(errors));
        }

        boolean isValid() {
            return valid;
        }

        Map<String, Object> getNormalized() {
            return normalized;
        }

        List<FieldError> getErrors() {
            return errors;
        }

        Map<String, Object> toErrorBody() {
            List<Map<String, String>> details = errors.stream()
                    .map(error -> Map.of(
                            "field", error.field(),
                            "message", error.message()))
                    .toList();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", ERROR_CODE);
            body.put("message", DEFAULT_MESSAGE);
            body.put("details", details);
            return body;
        }
    }

    record FieldError(String field, String message) {}

    private static class ValidationContext {
        private final HttpServletRequest request;
        private final Map<String, Object> payload;
        private final ObjectMapper mapper;

        private Map<String, Object> cachedBodyMap;
        private Map<String, Object> cachedHeaders;
        private Map<String, Object> cachedQuery;
        private Map<String, Object> cachedPath;

        ValidationContext(HttpServletRequest request,
                          Map<String, Object> payload,
                          ObjectMapper mapper) {
            this.request = request;
            this.payload = payload;
            this.mapper = mapper;
        }

        Map<String, Object> toMap(Object value) {
            if (value instanceof Map<?, ?> map) {
                LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                return copy;
            }
            if (value instanceof JsonNode node) {
                return mapper.convertValue(node, Map.class);
            }
            return null;
        }

        List<Object> toList(Object value) {
            if (value instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            if (value instanceof Object[] array) {
                return Arrays.asList(array);
            }
            if (value instanceof String text) {
                String trimmed = text.trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    try {
                        return mapper.readValue(trimmed, List.class);
                    } catch (IOException ignored) {
                    }
                }
            }
            return null;
        }

        Map<String, Object> pathMap() {
            if (cachedPath != null) {
                return cachedPath;
            }
            Object value = payload.get("path");
            cachedPath = toMap(value);
            return cachedPath == null ? Map.of() : cachedPath;
        }

        Map<String, Object> queryMap() {
            if (cachedQuery != null) {
                return cachedQuery;
            }
            Object value = payload.get("query");
            cachedQuery = toMap(value);
            return cachedQuery == null ? Map.of() : cachedQuery;
        }

        Map<String, Object> headersMap() {
            if (cachedHeaders != null) {
                return cachedHeaders;
            }
            Object value = payload.get("headers");
            Map<String, Object> rawMap = toMap(value);
            if (rawMap == null) {
                cachedHeaders = Map.of();
                return cachedHeaders;
            }
            Map<String, Object> lower = new LinkedHashMap<>();
            rawMap.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), v));
            cachedHeaders = lower;
            return cachedHeaders;
        }

        Object bodyAsObject() {
            return payload.get("body");
        }

        Map<String, Object> bodyMap() {
            if (cachedBodyMap != null) {
                return cachedBodyMap;
            }
            Object body = payload.get("body");
            Map<String, Object> map = toMap(body);
            if (map == null && body instanceof String text) {
                try {
                    map = mapper.readValue(text, Map.class);
                } catch (IOException ignored) {
                }
            }
            cachedBodyMap = map;
            return map;
        }

        Object lookupBody(JsonNode schema,
                          String fieldName,
                          Map<String, Object> currentMap,
                          boolean topLevel) {
            Map<String, Object> map = currentMap != null ? currentMap : bodyMap();
            if (map == null) {
                return null;
            }
            if (map.containsKey(fieldName)) {
                return map.get(fieldName);
            }
            if (topLevel && schema.path("properties").isObject()) {
                return map;
            }
            return map.get(fieldName);
        }

        Object lookupPath(JsonNode schema, String fieldName) {
            Map<String, Object> map = pathMap();
            String key = schema.path("x-pathVariable").asText(fieldName);
            return map.get(key);
        }

        Object lookupQuery(JsonNode schema, String fieldName) {
            Map<String, Object> map = queryMap();
            String key = schema.path("x-paramName").asText(fieldName);
            return map.get(key);
        }

        Object lookupHeader(JsonNode schema, String fieldName) {
            Map<String, Object> map = headersMap();
            String key = schema.path("x-headerName").asText(fieldName).toLowerCase(Locale.ROOT);
            return map.get(key);
        }

        Object lookupForm(JsonNode schema, String fieldName) {
            String key = schema.path("x-formField").asText(fieldName);
            String parameter = request.getParameter(key);
            if (parameter != null) {
                return parameter;
            }
            if (isMultipart(request)) {
                try {
                    Part part = request.getPart(key);
                    if (part != null) {
                        return part;
                    }
                } catch (IOException | ServletException ignored) {
                }
            }
            return null;
        }

        boolean isMultipart(HttpServletRequest request) {
            String contentType = request.getContentType();
            return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
        }
    }
}



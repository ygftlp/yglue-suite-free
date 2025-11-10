package org.yglue.flow.runtime.core.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static Map<String, Object> toMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> map.put(entry.getKey(), toObject(entry.getValue())));
        return map;
    }

    public static List<Object> toList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<Object> list = new ArrayList<>();
        for (JsonNode item : node) {
            list.add(toObject(item));
        }
        return list;
    }

    public static Object toObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isArray()) {
            return toList(node);
        }
        if (node.isObject()) {
            return toMap(node);
        }
        return node.asText();
    }
}

package org.yglue.flow.runtime.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();
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

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 待转换的对象
     * @return JSON字符串
     */
    public static String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将JSON字符串转换为指定类型的对象
     *
     * @param json   JSON字符串
     * @param clazz  目标类型Class
     * @param <T>    目标类型
     * @return 转换后的对象
     */
    public static <T> T toObject(String json, Class<T> clazz){
       try {
           return objectMapper.readValue(json, clazz);
       }catch (Exception e){
           return null;
       }
    }
}

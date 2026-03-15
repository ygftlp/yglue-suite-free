package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

class FlowRequestInputBuilder {

    private final ObjectMapper objectMapper;
    private final FlowRequestPayloadExtractor payloadExtractor;

    FlowRequestInputBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.payloadExtractor = new FlowRequestPayloadExtractor(objectMapper);
    }

    Map<String, Object> buildFlowInput(HttpServletRequest request, boolean readBody) throws IOException {
        Map<String, Object> flowInput = new LinkedHashMap<>();
        flowInput.put("request", payloadExtractor.extract(request, readBody));
        return flowInput;
    }

    Map<String, Object> buildFlowInput(HttpServletRequest request, Parameter[] parameters, Object[] args)
            throws IOException {
        Map<String, Object> requestPayload = payloadExtractor.extract(request, false);
        applyMethodOverrides(requestPayload, extractMethodOverrides(parameters, args));

        Map<String, Object> flowInput = new LinkedHashMap<>();
        flowInput.put("request", requestPayload);
        return flowInput;
    }

    Map<String, Object> extractMethodOverrides(Parameter[] parameters, Object[] args) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        if (parameters == null || args == null) {
            return overrides;
        }

        int count = Math.min(parameters.length, args.length);
        for (int i = 0; i < count; i++) {
            Parameter param = parameters[i];
            Object argValue = args[i];

            PathVariable pathVar = param.getAnnotation(PathVariable.class);
            RequestParam requestParam = param.getAnnotation(RequestParam.class);
            RequestHeader requestHeader = param.getAnnotation(RequestHeader.class);
            RequestBody requestBody = param.getAnnotation(RequestBody.class);

            String paramName = getParameterName(param, pathVar, requestParam, requestHeader);
            if (pathVar != null) {
                overrides.put("path." + paramName, argValue);
                continue;
            }
            if (requestParam != null) {
                overrides.put("query." + paramName, argValue);
                continue;
            }
            if (requestHeader != null) {
                String headerName = requestHeader.value().isEmpty() ? paramName : requestHeader.value();
                overrides.put("headers." + headerName, argValue);
                continue;
            }
            if (requestBody != null) {
                overrides.put("body", convertBodyValue(argValue));
            }
        }
        return overrides;
    }

    private void applyMethodOverrides(Map<String, Object> requestPayload, Map<String, Object> overrides) {
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("path.")) {
                section(requestPayload, "path").put(key.substring(5), entry.getValue());
                continue;
            }
            if (key.startsWith("query.")) {
                section(requestPayload, "query").put(key.substring(6), entry.getValue());
                continue;
            }
            if (key.startsWith("headers.")) {
                section(requestPayload, "headers").put(key.substring(8), entry.getValue());
                continue;
            }
            if ("body".equals(key)) {
                requestPayload.put("body", entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(Map<String, Object> requestPayload, String key) {
        Object existing = requestPayload.get(key);
        if (existing instanceof Map<?, ?> map) {
            if (existing instanceof LinkedHashMap<?, ?>) {
                return (Map<String, Object>) map;
            }
            Map<String, Object> copied = new LinkedHashMap<>((Map<String, Object>) map);
            requestPayload.put(key, copied);
            return copied;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        requestPayload.put(key, created);
        return created;
    }

    private String getParameterName(Parameter param,
            PathVariable pathVar,
            RequestParam requestParam,
            RequestHeader requestHeader) {
        if (pathVar != null && !pathVar.value().isEmpty()) {
            return pathVar.value();
        }
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        if (requestHeader != null && !requestHeader.value().isEmpty()) {
            return requestHeader.value();
        }
        return param.getName();
    }

    private Object convertBodyValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
            return objectMapper.convertValue(value, Object.class);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        try {
            return objectMapper.convertValue(value, Object.class);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}

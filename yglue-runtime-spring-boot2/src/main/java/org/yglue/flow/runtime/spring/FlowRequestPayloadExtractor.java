package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class FlowRequestPayloadExtractor {

    private final ObjectMapper objectMapper;

    FlowRequestPayloadExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, Object> extract(HttpServletRequest request, boolean readBody) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", request.getMethod());
        payload.put("uri", request.getRequestURI());
        payload.put("query", extractQueryParameters(request));

        Object pathVars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVars instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pathVarMap = (Map<String, Object>) pathVars;
            payload.put("path", new LinkedHashMap<>(pathVarMap));
        } else {
            // Java 8 兼容：使用 Collections.emptyMap() 替代 Map.of()
            payload.put("path", new LinkedHashMap<>());
        }

        payload.put("headers", extractHeaders(request));

        if (readBody && allowsRequestBody(request.getMethod())) {
            payload.put("body", extractRequestBody(request));
        } else {
            payload.put("body", null);
        }
        return payload;
    }

    private Map<String, Object> extractQueryParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, Object> result = new LinkedHashMap<>();
        parameterMap.forEach((key, values) -> {
            if (values == null) {
                result.put(key, null);
            } else if (values.length == 1) {
                result.put(key, values[0]);
            } else {
                result.put(key, List.of(values));
            }
        });
        return result;
    }

    private Map<String, Object> extractHeaders(HttpServletRequest request) {
        Map<String, Object> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            Enumeration<String> headerValues = request.getHeaders(name);
            List<String> values = headerValues == null ? List.of() : java.util.Collections.list(headerValues);
            headers.put(name, values.size() == 1 ? values.get(0) : values);
        }
        return headers;
    }

    private Object extractRequestBody(HttpServletRequest request) throws IOException {
        String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) {
            return null;
        }
        if (isJsonContent(request.getContentType())) {
            try {
                return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
            }
        }
        return body;
    }

    private boolean isJsonContent(String contentType) {
        if (contentType == null) {
            return false;
        }
        MediaType mediaType = MediaType.parseMediaType(contentType);
        return MediaType.APPLICATION_JSON.includes(mediaType) || mediaType.getSubtype().endsWith("+json");
    }

    private boolean allowsRequestBody(String method) {
        if (method == null) {
            return false;
        }
        String upper = method.toUpperCase();
        return "POST".equals(upper)
                || "PUT".equals(upper)
                || "PATCH".equals(upper)
                || "DELETE".equals(upper);
    }
}

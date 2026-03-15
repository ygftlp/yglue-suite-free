package org.yglue.flow.runtime.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.yglue.flow.runtime.core.definition.RestEntryPoint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable request view exposed to {@link InboundRequestInterceptor}
 * implementations.
 */
public class InboundRequestContext {

    private final HttpServletRequest request;
    private final RestEntryPoint entryPoint;
    private final Map<String, Object> payload;

    InboundRequestContext(HttpServletRequest request,
                          RestEntryPoint entryPoint,
                          Map<String, Object> payload) {
        this.request = request;
        this.entryPoint = entryPoint;
        this.payload = payload;
    }

    public HttpServletRequest request() {
        return request;
    }

    public RestEntryPoint entryPoint() {
        return entryPoint;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    @SuppressWarnings("unchecked")
    public void put(String path, Object value) {
        if (path == null || path.isBlank()) {
            return;
        }
        String[] segments = path.split("\\.");
        if (segments.length == 0) {
            return;
        }
        Map<String, Object> cursor = payload;
        for (int i = 0; i < segments.length - 1; i++) {
            String key = segments[i].trim();
            if (key.isEmpty()) {
                return;
            }
            Object next = cursor.get(key);
            if (!(next instanceof Map<?, ?>)) {
                Map<String, Object> created = new LinkedHashMap<>();
                cursor.put(key, created);
                cursor = created;
                continue;
            }
            cursor = (Map<String, Object>) next;
        }
        String tail = segments[segments.length - 1].trim();
        if (tail.isEmpty()) {
            return;
        }
        cursor.put(tail, value);
    }
}

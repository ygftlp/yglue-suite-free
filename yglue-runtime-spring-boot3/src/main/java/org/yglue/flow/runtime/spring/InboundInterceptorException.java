package org.yglue.flow.runtime.spring;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exception type business interceptors can throw to stop request dispatch with
 * a controlled HTTP response.
 */
public class InboundInterceptorException extends RuntimeException {

    private final int status;
    private final Map<String, Object> body;

    InboundInterceptorException(int status, Map<String, Object> body) {
        super(body != null ? String.valueOf(body.getOrDefault("message", "Inbound interceptor error")) : "Inbound interceptor error");
        this.status = status;
        this.body = body == null ? Map.of("message", "Inbound interceptor error") : Map.copyOf(body);
    }

    public int getStatus() {
        return status;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public static InboundInterceptorException badRequest(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "FLOW_BAD_REQUEST");
        body.put("message", message);
        return new InboundInterceptorException(400, body);
    }

    public static InboundInterceptorException unauthorized(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "FLOW_UNAUTHORIZED");
        body.put("message", message);
        return new InboundInterceptorException(401, body);
    }
}

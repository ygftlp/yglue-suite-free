package org.yglue.flow.runtime.spring;

import java.util.LinkedHashMap;
import java.util.Map;

class InboundInterceptorException extends RuntimeException {

    private final int status;
    private final Map<String, Object> body;

    InboundInterceptorException(int status, Map<String, Object> body) {
        super(body != null ? String.valueOf(body.getOrDefault("message", "Inbound interceptor error")) : "Inbound interceptor error");
        this.status = status;
        this.body = body == null ? Map.of("message", "Inbound interceptor error") : Map.copyOf(body);
    }

    int getStatus() {
        return status;
    }

    Map<String, Object> getBody() {
        return body;
    }

    static InboundInterceptorException badRequest(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "FLOW_BAD_REQUEST");
        body.put("message", message);
        return new InboundInterceptorException(400, body);
    }

    static InboundInterceptorException unauthorized(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "FLOW_UNAUTHORIZED");
        body.put("message", message);
        return new InboundInterceptorException(401, body);
    }
}


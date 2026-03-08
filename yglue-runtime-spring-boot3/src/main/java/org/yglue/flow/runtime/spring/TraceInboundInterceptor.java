package org.yglue.flow.runtime.spring;

import java.util.Map;
import java.util.UUID;

class TraceInboundInterceptor implements InboundRequestInterceptor {

    @Override
    public String code() {
        return "trace";
    }

    @Override
    public void apply(InboundRequestContext context, Map<String, Object> config) {
        String headerName = readString(config, "traceHeader", "X-Trace-Id");
        String outputPath = readString(config, "outputPath", "request.traceId");
        String traceId = context.request().getHeader(headerName);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        context.put(outputPath, traceId);
    }

    private String readString(Map<String, Object> config, String key, String fallback) {
        if (config == null) return fallback;
        Object value = config.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}


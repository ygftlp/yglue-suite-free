package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.definition.RestEntryPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class InboundInterceptorChain {

    private static final Logger log = LoggerFactory.getLogger(InboundInterceptorChain.class);

    private final ObjectMapper objectMapper;
    private final Map<String, InboundRequestInterceptor> interceptorMap;

    InboundInterceptorChain(ObjectMapper objectMapper,
                            List<InboundRequestInterceptor> interceptors) {
        this.objectMapper = objectMapper;
        this.interceptorMap = interceptors == null
                ? Map.of()
                : interceptors.stream().collect(Collectors.toMap(
                        InboundRequestInterceptor::code,
                        it -> it,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    void apply(RestEntryPoint entryPoint, HttpServletRequest request, Map<String, Object> payload) {
        if (entryPoint == null || payload == null) {
            return;
        }
        List<InboundInterceptorSpec> specs = resolveSpecs(entryPoint);
        if (specs.isEmpty()) {
            return;
        }
        InboundRequestContext context = new InboundRequestContext(request, entryPoint, payload);
        for (InboundInterceptorSpec spec : specs) {
            if (!spec.enabled()) {
                continue;
            }
            InboundRequestInterceptor interceptor = interceptorMap.get(spec.code());
            if (interceptor == null) {
                log.warn("Unknown inbound interceptor code: {}", spec.code());
                continue;
            }
            interceptor.apply(context, spec.config());
        }
    }

    private List<InboundInterceptorSpec> resolveSpecs(RestEntryPoint entryPoint) {
        List<Map<String, Object>> items = new ArrayList<>(entryPoint.inboundInterceptorsView());
        Object raw = entryPoint.getInboundInterceptors();
        if (items.isEmpty() && raw instanceof String rawText && !rawText.isBlank()) {
            items = parseSpecsFromString(rawText);
        }
        if (items.isEmpty()) {
            return List.of(new InboundInterceptorSpec("schemaNormalize", true, 400, Map.of()));
        }
        return items.stream()
                .map(this::toSpec)
                .filter(spec -> spec.code() != null && !spec.code().isBlank())
                .sorted(Comparator.comparingInt(InboundInterceptorSpec::order))
                .toList();
    }

    private List<Map<String, Object>> parseSpecsFromString(String rawText) {
        try {
            JsonNode node = objectMapper.readTree(rawText);
            if (!node.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> specs = new ArrayList<>();
            for (JsonNode itemNode : node) {
                Map<String, Object> item = objectMapper.convertValue(itemNode, Map.class);
                specs.add(item);
            }
            return specs;
        } catch (Exception ex) {
            log.warn("Failed to parse inbound interceptors JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private InboundInterceptorSpec toSpec(Map<String, Object> item) {
        String code = asText(item.get("code"));
        boolean enabled = asBoolean(item.get("enabled"), true);
        int order = asInt(item.get("order"), 1000);
        Map<String, Object> config = item.get("config") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return new InboundInterceptorSpec(code, enabled, order, config);
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int asInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private record InboundInterceptorSpec(String code, boolean enabled, int order, Map<String, Object> config) {
    }
}


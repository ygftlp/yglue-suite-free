package org.yglue.flow.runtime.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HttpRestInvocationStrategy implements RestInvocationStrategy {

    private static final Set<String> NO_BODY_METHODS = Set.of("GET", "DELETE", "HEAD", "OPTIONS", "TRACE");

    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public HttpRestInvocationStrategy() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper());
    }

    public HttpRestInvocationStrategy(HttpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(RestInvocationContext context) {
        Map<String, Object> cfg = context.getConfig();
        return cfg.containsKey("url");
    }

    @Override
    public Object invoke(RestInvocationContext context) throws Exception {
        Map<String, Object> cfg = new HashMap<>(context.getConfig());
        int retryCount = parseNonNegativeInt(evaluate(cfg.getOrDefault("retryCount", 0), context), 0);
        long retryBackoffMs = parseNonNegativeLong(evaluate(cfg.getOrDefault("retryBackoffMs", 0), context), 0L);
        Set<Integer> retryStatuses = parseRetryStatuses(cfg.get("retryOnStatuses"), context);

        int maxAttempts = Math.max(1, retryCount + 1);
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                RequestPlan plan = buildRequestPlan(cfg, context);
                HttpResponse<String> response = client.send(plan.request(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return parseResponseBody(response.body());
                }

                if (attempt < maxAttempts && shouldRetryStatus(status, retryStatuses)) {
                    sleepBackoff(retryBackoffMs);
                    continue;
                }
                throw new IllegalStateException("HTTP call failed: " + status + " - " + response.body());
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
                if (attempt < maxAttempts && shouldRetryException(ex)) {
                    lastException = ex;
                    sleepBackoff(retryBackoffMs);
                    continue;
                }
                throw ex;
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("HTTP call failed after retries");
    }

    private RequestPlan buildRequestPlan(Map<String, Object> cfg, RestInvocationContext context) throws Exception {
        String method = String.valueOf(cfg.getOrDefault("method", "GET")).toUpperCase();
        String urlTemplate = String.valueOf(cfg.get("url"));
        String url = String.valueOf(evaluate(urlTemplate, context));
        url = appendQuery(url, cfg.getOrDefault("query", cfg.get("queryParams")), context);

        Object timeoutCfg = cfg.getOrDefault("timeoutSeconds", 30);
        long timeoutSeconds = parsePositiveLong(evaluate(timeoutCfg, context), 30L);

        Object bodyCfg = cfg.containsKey("body") ? cfg.get("body") : cfg.get("bodyJson");
        Object evaluatedBody = evaluate(bodyCfg, context);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds));

        boolean hasContentType = applyHeaders(builder, cfg.get("headers"), context);
        HttpRequest.BodyPublisher body = buildBody(method, evaluatedBody);
        if (!hasContentType && shouldSetJsonContentType(method, evaluatedBody)) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body);
        return new RequestPlan(builder.build());
    }

    private Object parseResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, Object.class);
        } catch (Exception ignored) {
            return responseBody;
        }
    }

    private boolean shouldRetryStatus(int status, Set<Integer> retryStatuses) {
        if (!retryStatuses.isEmpty()) {
            return retryStatuses.contains(status);
        }
        return status >= 500 || status == 429;
    }

    private boolean shouldRetryException(Exception ex) {
        return !(ex instanceof IllegalArgumentException);
    }

    private void sleepBackoff(long backoffMs) throws InterruptedException {
        if (backoffMs <= 0L) {
            return;
        }
        Thread.sleep(backoffMs);
    }

    private boolean applyHeaders(HttpRequest.Builder builder, Object headers, RestInvocationContext context) {
        boolean hasContentType = false;
        if (headers instanceof Map<?, ?> headerMap) {
            for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String value = String.valueOf(evaluate(entry.getValue(), context));
                if (isBlank(key)) {
                    continue;
                }
                if ("content-type".equalsIgnoreCase(key)) {
                    hasContentType = true;
                }
                builder.header(key, value);
            }
            return hasContentType;
        }
        if (headers instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String key = String.valueOf(map.getOrDefault("key", map.get("name")));
                Object rawValue = map.containsKey("value") ? map.get("value") : map.get("val");
                if (isBlank(key)) {
                    continue;
                }
                String value = String.valueOf(evaluate(rawValue, context));
                if ("content-type".equalsIgnoreCase(key)) {
                    hasContentType = true;
                }
                builder.header(key, value);
            }
        }
        return hasContentType;
    }

    private String appendQuery(String baseUrl, Object queryConfig, RestInvocationContext context) {
        List<String> pairs = new ArrayList<>();
        if (queryConfig instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = evaluate(entry.getValue(), context);
                appendQueryPair(pairs, key, value);
            }
        } else if (queryConfig instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String key = String.valueOf(map.getOrDefault("key", map.get("name")));
                Object rawValue = map.containsKey("value") ? map.get("value") : map.get("val");
                Object value = evaluate(rawValue, context);
                appendQueryPair(pairs, key, value);
            }
        }
        if (pairs.isEmpty()) {
            return baseUrl;
        }
        String join = String.join("&", pairs);
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + join;
    }

    private void appendQueryPair(List<String> pairs, String key, Object value) {
        if (isBlank(key) || value == null) {
            return;
        }
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        String encodedValue = URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
        pairs.add(encodedKey + "=" + encodedValue);
    }

    private HttpRequest.BodyPublisher buildBody(String method, Object body) throws Exception {
        if (NO_BODY_METHODS.contains(method.toUpperCase())) {
            return HttpRequest.BodyPublishers.noBody();
        }
        if (body == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        if (body instanceof byte[] bytes) {
            return HttpRequest.BodyPublishers.ofByteArray(bytes);
        }
        if (body instanceof String str) {
            return HttpRequest.BodyPublishers.ofString(str, StandardCharsets.UTF_8);
        }
        return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8);
    }

    private boolean shouldSetJsonContentType(String method, Object body) {
        if (NO_BODY_METHODS.contains(method.toUpperCase())) {
            return false;
        }
        return body != null && !(body instanceof byte[]) && !(body instanceof String);
    }

    private Set<Integer> parseRetryStatuses(Object raw, RestInvocationContext context) {
        Object evaluated = evaluate(raw, context);
        Set<Integer> result = new LinkedHashSet<>();
        if (evaluated instanceof List<?> list) {
            for (Object item : list) {
                Integer code = parseInt(item);
                if (code != null && code > 0) {
                    result.add(code);
                }
            }
            return result;
        }
        if (evaluated instanceof String text) {
            String[] split = text.split(",");
            for (String part : split) {
                Integer code = parseInt(part.trim());
                if (code != null && code > 0) {
                    result.add(code);
                }
            }
        } else {
            Integer code = parseInt(evaluated);
            if (code != null && code > 0) {
                result.add(code);
            }
        }
        return result;
    }

    private Object evaluate(Object value, RestInvocationContext context) {
        return ExpressionEvaluator.evaluate(value, context.getContext());
    }

    private long parsePositiveLong(Object raw, long fallback) {
        Long parsed = parseLong(raw);
        if (parsed == null || parsed <= 0L) {
            return fallback;
        }
        return parsed;
    }

    private long parseNonNegativeLong(Object raw, long fallback) {
        Long parsed = parseLong(raw);
        if (parsed == null || parsed < 0L) {
            return fallback;
        }
        return parsed;
    }

    private int parseNonNegativeInt(Object raw, int fallback) {
        Integer parsed = parseInt(raw);
        if (parsed == null || parsed < 0) {
            return fallback;
        }
        return parsed;
    }

    private Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseInt(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RequestPlan(HttpRequest request) {
    }
}

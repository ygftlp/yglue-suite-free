package org.yglue.flow.runtime.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRestInvocationStrategy implements RestInvocationStrategy {

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
        String urlTemplate = String.valueOf(cfg.get("url"));
        String method = String.valueOf(cfg.getOrDefault("method", "GET"));
        Object bodyCfg = cfg.get("body");
        Object evaluatedBody = ExpressionEvaluator.evaluate(bodyCfg, context.getContext());

        String url = String.valueOf(ExpressionEvaluator.evaluate(urlTemplate, context.getContext()));
        Object timeoutCfg = cfg.getOrDefault("timeoutSeconds", 30);
        Object evaluatedTimeout = ExpressionEvaluator.evaluate(timeoutCfg, context.getContext());
        long timeoutSeconds = Long.parseLong(String.valueOf(evaluatedTimeout));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .method(method.toUpperCase(), buildBody(method, evaluatedBody));

        Object headers = cfg.get("headers");
        if (headers instanceof Map<?, ?> headerMap) {
            headerMap.forEach((k, v) -> builder.header(String.valueOf(k),
                    String.valueOf(ExpressionEvaluator.evaluate(v, context.getContext()))));
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            return objectMapper.readValue(responseBody, Object.class);
        }
        throw new IllegalStateException("HTTP call failed: " + status + " - " + response.body());
    }

    private HttpRequest.BodyPublisher buildBody(String method, Object body) throws Exception {
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
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
}

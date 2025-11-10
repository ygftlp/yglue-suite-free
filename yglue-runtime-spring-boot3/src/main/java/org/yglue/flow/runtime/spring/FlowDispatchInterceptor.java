package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.yglue.flow.runtime.RuleEngine;
import org.yglue.flow.runtime.core.FlowExecutionResult;

import java.io.IOException;
import java.util.Map;

class FlowDispatchInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FlowDispatchInterceptor.class);

    private final RuleEngine ruleEngine;
    private final FlowRuntimeProperties properties;
    private final FlowRequestPayloadExtractor payloadExtractor;
    private final ObjectMapper objectMapper;
    private final RestEntryPointRegistry registry;

    FlowDispatchInterceptor(RuleEngine ruleEngine,
                            FlowRuntimeProperties properties,
                            ObjectMapper objectMapper,
                            RestEntryPointRegistry registry) {
        this.ruleEngine = ruleEngine;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.payloadExtractor = new FlowRequestPayloadExtractor(objectMapper);
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        MatchContext matchContext = resolveMatch(request, handler);
        if (matchContext == null) {
            return true;
        }
        String ruleId = matchContext.ruleId();
        Map<String, Object> payload = payloadExtractor.extract(request, true);
        // 将请求参数包装在 "request" 键下，以便通过 request.path.xxx, request.body.xxx 等方式访问
        Map<String, Object> flowInput = new java.util.LinkedHashMap<>();
        flowInput.put("request", payload);
        FlowExecutionResult result = ruleEngine.execute(ruleId, flowInput);
        if (log.isInfoEnabled()) {
            log.info("yglue flow executed for ruleId={} {} {}", ruleId, request.getMethod(), request.getRequestURI());
        }
        writeResponse(response, result);
        return false;
    }

    private MatchContext resolveMatch(HttpServletRequest request, Object handler) {
        FlowOrchestrated annotation = resolveAnnotation(handler);
        if (annotation != null) {
            if (log.isDebugEnabled()) {
                log.debug("Matched flow via annotation for {} {}", request.getMethod(), request.getRequestURI());
            }
            return new MatchContext(annotation.ruleId());
        }
        if (registry == null) {
            return null;
        }
        return registry.findMatch(request.getMethod(), request.getRequestURI())
                .map(entry -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Matched flow via entrypoint {} {} -> {}", request.getMethod(), request.getRequestURI(), entry.getFlowCode());
                    }
                    return new MatchContext(entry.getFlowCode());
                })
                .orElse(null);
    }

    private FlowOrchestrated resolveAnnotation(Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return null;
        }
        FlowOrchestrated annotation = method.getMethodAnnotation(FlowOrchestrated.class);
        if (annotation != null) {
            return annotation;
        }
        return method.getBeanType().getAnnotation(FlowOrchestrated.class);
    }

    private void writeResponse(HttpServletResponse response, FlowExecutionResult result) throws IOException {
        Object returnValue = result.getReturnValue();
        if (returnValue == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        // 如果是简单类型，直接写回文本，避免被 JSON 包裹
        if (returnValue instanceof String
                || returnValue instanceof Number
                || returnValue instanceof Boolean) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
            response.getWriter().write(String.valueOf(returnValue));
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), returnValue);
    }

    private record MatchContext(String ruleId) {}
}

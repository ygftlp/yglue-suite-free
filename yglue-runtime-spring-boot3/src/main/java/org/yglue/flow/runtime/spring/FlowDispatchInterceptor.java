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
import org.yglue.flow.runtime.core.definition.RestEntryPoint;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

class FlowDispatchInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FlowDispatchInterceptor.class);

    private final RuleEngine ruleEngine;
    private final FlowRequestPayloadExtractor payloadExtractor;
    private final ObjectMapper objectMapper;
    private final RestEntryPointRegistry registry;
    private final InboundInterceptorChain inboundInterceptorChain;

    FlowDispatchInterceptor(RuleEngine ruleEngine,
                            ObjectMapper objectMapper,
                            RestEntryPointRegistry registry,
                            InboundInterceptorChain inboundInterceptorChain) {
        this.ruleEngine = ruleEngine;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.inboundInterceptorChain = inboundInterceptorChain;
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
        Map<String, Object> requestPayload = payloadExtractor.extract(request, true);
        try {
            inboundInterceptorChain.apply(matchContext.entryPoint(), request, requestPayload);
        } catch (InboundInterceptorException ex) {
            writeInboundError(response, ex);
            return false;
        }

        Map<String, Object> flowInput = new LinkedHashMap<>();
        flowInput.put("request", requestPayload);

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
                log.debug("Matched flow via @FlowOrchestrated for {} {}", request.getMethod(), request.getRequestURI());
            }
            return new MatchContext(annotation.ruleId(), null);
        }
        if (registry == null) {
            return null;
        }
        return registry.findMatch(request.getMethod(), request.getRequestURI())
                .map(entry -> new MatchContext(entry.getFlowCode(), entry))
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
        if (returnValue instanceof String || returnValue instanceof Number || returnValue instanceof Boolean) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
            response.getWriter().write(String.valueOf(returnValue));
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), returnValue);
    }

    private void writeInboundError(HttpServletResponse response,
                                   InboundInterceptorException ex) throws IOException {
        response.setStatus(ex.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ex.getBody());
    }

    private record MatchContext(String ruleId, RestEntryPoint entryPoint) {
    }
}

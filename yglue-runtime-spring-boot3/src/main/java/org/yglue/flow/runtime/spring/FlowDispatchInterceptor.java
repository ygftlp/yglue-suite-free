package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.yglue.flow.runtime.RuleEngine;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.definition.RestEntryPoint;

import java.util.Map;

class FlowDispatchInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FlowDispatchInterceptor.class);

    private final RuleEngine ruleEngine;
    private final FlowRequestInputBuilder requestInputBuilder;
    private final FlowHttpResponseWriter responseWriter;
    private final RestEntryPointRegistry registry;
    private final InboundInterceptorChain inboundInterceptorChain;

    FlowDispatchInterceptor(RuleEngine ruleEngine,
                            ObjectMapper objectMapper,
                            RestEntryPointRegistry registry,
                            InboundInterceptorChain inboundInterceptorChain) {
        this.ruleEngine = ruleEngine;
        this.registry = registry;
        this.inboundInterceptorChain = inboundInterceptorChain;
        this.requestInputBuilder = new FlowRequestInputBuilder(objectMapper);
        this.responseWriter = new FlowHttpResponseWriter(objectMapper);
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
        Map<String, Object> flowInput = requestInputBuilder.buildFlowInput(request, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> requestPayload = (Map<String, Object>) flowInput.get("request");
        try {
            inboundInterceptorChain.apply(matchContext.entryPoint(), request, requestPayload);
        } catch (InboundInterceptorException ex) {
            responseWriter.writeInboundError(response, ex);
            return false;
        }

        FlowExecutionResult result = ruleEngine.execute(ruleId, flowInput);
        if (log.isInfoEnabled()) {
            log.info("yglue flow executed for ruleId={} {} {}", ruleId, request.getMethod(), request.getRequestURI());
        }
        responseWriter.writeFlowResult(response, result, matchContext.entryPoint());
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

    private record MatchContext(String ruleId, RestEntryPoint entryPoint) {
    }
}

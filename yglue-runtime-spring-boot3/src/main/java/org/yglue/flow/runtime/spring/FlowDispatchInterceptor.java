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
import java.util.Map;

class FlowDispatchInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FlowDispatchInterceptor.class);

    private final RuleEngine ruleEngine;
    private final FlowRequestPayloadExtractor payloadExtractor;
    private final ObjectMapper objectMapper;
    private final RestEntryPointRegistry registry;
    private final RequestSchemaValidator requestSchemaValidator;

    FlowDispatchInterceptor(RuleEngine ruleEngine,
                            ObjectMapper objectMapper,
                            RestEntryPointRegistry registry,
                            RequestSchemaValidator requestSchemaValidator) {
        this.ruleEngine = ruleEngine;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.requestSchemaValidator = requestSchemaValidator;
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
        if (matchContext.entryPoint() != null && requestSchemaValidator != null) {
            RequestSchemaValidator.ValidationResult validationResult =
                    requestSchemaValidator.validate(matchContext.entryPoint(), request, payload);
            if (!validationResult.isValid()) {
                writeValidationError(response, validationResult);
                return false;
            }
            if (validationResult.getNormalized() != null) {
                payload.put("params", validationResult.getNormalized());
            }
        }
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
        // 如果是 HandlerMethod（REST Controller 方法），由 AOP 切面处理，拦截器不再处理
        if (handler instanceof HandlerMethod) {
            if (log.isDebugEnabled()) {
                log.debug("HandlerMethod detected, will be handled by AOP aspect for {} {}", 
                         request.getMethod(), request.getRequestURI());
            }
            return null; // 返回 null，让拦截器不处理，由 AOP 切面处理
        }
        
        // 对于非 HandlerMethod 的处理器（如静态资源等），继续使用拦截器查找流程规则
        if (registry == null) {
            return null;
        }
        return registry.findMatch(request.getMethod(), request.getRequestURI())
                .map(entry -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Matched flow via entrypoint {} {} -> {}", request.getMethod(), request.getRequestURI(), entry.getFlowCode());
                    }
                    return new MatchContext(entry.getFlowCode(), entry);
                })
                .orElse(null);
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

    private void writeValidationError(HttpServletResponse response,
                                      RequestSchemaValidator.ValidationResult validationResult) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), validationResult.toErrorBody());
    }

    private record MatchContext(String ruleId, org.yglue.flow.runtime.core.definition.RestEntryPoint entryPoint) {}
}

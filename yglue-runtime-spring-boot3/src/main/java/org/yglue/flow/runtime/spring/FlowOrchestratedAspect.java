package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.yglue.flow.runtime.RuleEngine;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.definition.RestEntryPoint;
import org.yglue.flow.runtime.core.validator.ValidationException;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

@Aspect
@Order(1)
public class FlowOrchestratedAspect {

    private static final Logger log = LoggerFactory.getLogger(FlowOrchestratedAspect.class);

    private final RuleEngine ruleEngine;
    private final RestEntryPointRegistry entryPointRegistry;
    private final InboundInterceptorChain inboundInterceptorChain;
    private final FlowRequestInputBuilder requestInputBuilder;
    private final FlowHttpResponseWriter responseWriter;

    public FlowOrchestratedAspect(RuleEngine ruleEngine,
            ObjectMapper objectMapper,
            RestEntryPointRegistry entryPointRegistry,
            InboundInterceptorChain inboundInterceptorChain) {
        this.ruleEngine = ruleEngine;
        this.entryPointRegistry = entryPointRegistry;
        this.inboundInterceptorChain = inboundInterceptorChain;
        this.requestInputBuilder = new FlowRequestInputBuilder(objectMapper);
        this.responseWriter = new FlowHttpResponseWriter(objectMapper);
    }

    @Around("@annotation(org.yglue.flow.runtime.spring.FlowOrchestrated) || " +
            "@within(org.yglue.flow.runtime.spring.FlowOrchestrated)")
    public Object interceptFlowOrchestratedMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        FlowOrchestrated annotation = resolveFlowOrchestrated(joinPoint);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        if (response == null) {
            return joinPoint.proceed();
        }

        String httpMethod = request.getMethod();
        String path = request.getRequestURI();
        RestEntryPoint entryPoint = null;
        if (entryPointRegistry != null) {
            Optional<RestEntryPoint> entryPointOpt = entryPointRegistry.findMatch(httpMethod, path);
            entryPoint = entryPointOpt.orElse(null);
        }

        String ruleId = annotation.ruleId() == null ? "" : annotation.ruleId().trim();
        if (ruleId.isBlank() && entryPoint != null) {
            ruleId = entryPoint.getFlowCode();
        }
        if (ruleId == null || ruleId.isBlank()) {
            log.warn("No ruleId resolved for @FlowOrchestrated method on {} {}", httpMethod, path);
            return joinPoint.proceed();
        }
        if (log.isDebugEnabled()) {
            log.debug("Flow rule matched for @FlowOrchestrated {} {} -> {}", httpMethod, path, ruleId);
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method methodObj = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> flowInput = requestInputBuilder.buildFlowInput(request, methodObj.getParameters(), args);
        Object requestObj = flowInput.get("request");
        if (requestObj instanceof Map<?, ?> requestPayloadRaw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestPayload = (Map<String, Object>) requestPayloadRaw;
            try {
                inboundInterceptorChain.apply(entryPoint, request, requestPayload);
            } catch (InboundInterceptorException ex) {
                responseWriter.writeInboundError(response, ex);
                return null;
            }
        }

        try {
            FlowExecutionResult result = ruleEngine.execute(ruleId, flowInput);
            if (log.isInfoEnabled()) {
                log.info("yglue flow executed for ruleId={} {} {}", ruleId, httpMethod, path);
            }
            responseWriter.writeFlowResult(response, result, entryPoint);
            return null;
        } catch (ValidationException ex) {
            log.warn("Validation failed for ruleId={} {} {}: {}", ruleId, httpMethod, path, ex.getMessage());
            responseWriter.writeValidationError(response, ex, entryPoint);
            return null;
        } catch (Exception ex) {
            log.error("Flow execution failed for ruleId={} {} {}", ruleId, httpMethod, path, ex);
            responseWriter.writeGeneralError(response, ex, entryPoint);
            return null;
        }
    }

    private FlowOrchestrated resolveFlowOrchestrated(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return null;
        }
        Method method = signature.getMethod();
        FlowOrchestrated annotation = method.getAnnotation(FlowOrchestrated.class);
        if (annotation != null) {
            return annotation;
        }
        Class<?> targetType = joinPoint.getTarget() != null ? joinPoint.getTarget().getClass()
                : method.getDeclaringClass();
        return targetType.getAnnotation(FlowOrchestrated.class);
    }

}

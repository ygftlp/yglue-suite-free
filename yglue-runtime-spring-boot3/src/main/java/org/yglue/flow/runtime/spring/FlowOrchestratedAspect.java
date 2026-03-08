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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.yglue.flow.runtime.RuleEngine;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.definition.ErrorResponseFormat;
import org.yglue.flow.runtime.core.definition.RestEntryPoint;
import org.yglue.flow.runtime.core.validator.ValidationException;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Aspect
@Order(1)
public class FlowOrchestratedAspect {

    private static final Logger log = LoggerFactory.getLogger(FlowOrchestratedAspect.class);

    private final RuleEngine ruleEngine;
    private final ObjectMapper objectMapper;
    private final RestEntryPointRegistry entryPointRegistry;
    private final InboundInterceptorChain inboundInterceptorChain;

    public FlowOrchestratedAspect(RuleEngine ruleEngine,
            ObjectMapper objectMapper,
            RestEntryPointRegistry entryPointRegistry,
            InboundInterceptorChain inboundInterceptorChain) {
        this.ruleEngine = ruleEngine;
        this.objectMapper = objectMapper;
        this.entryPointRegistry = entryPointRegistry;
        this.inboundInterceptorChain = inboundInterceptorChain;
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
        Parameter[] parameters = methodObj.getParameters();

        Map<String, Object> methodParams = extractMethodParameters(parameters, args);
        Map<String, Object> flowInput = buildFlowInput(request, methodParams);
        Object requestObj = flowInput.get("request");
        if (requestObj instanceof Map<?, ?> requestPayloadRaw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestPayload = (Map<String, Object>) requestPayloadRaw;
            try {
                inboundInterceptorChain.apply(entryPoint, request, requestPayload);
            } catch (InboundInterceptorException ex) {
                writeInboundError(response, ex);
                return null;
            }
        }

        try {
            FlowExecutionResult result = ruleEngine.execute(ruleId, flowInput);
            if (log.isInfoEnabled()) {
                log.info("yglue flow executed for ruleId={} {} {}", ruleId, httpMethod, path);
            }
            writeResponse(response, result, entryPoint);
            return null;
        } catch (ValidationException ex) {
            log.warn("Validation failed for ruleId={} {} {}: {}", ruleId, httpMethod, path, ex.getMessage());
            writeValidationError(response, ex, entryPoint);
            return null;
        } catch (Exception ex) {
            log.error("Flow execution failed for ruleId={} {} {}", ruleId, httpMethod, path, ex);
            writeGeneralError(response, ex, entryPoint);
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

    private Map<String, Object> extractMethodParameters(Parameter[] parameters, Object[] args) {
        Map<String, Object> methodParams = new LinkedHashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Object argValue = args[i];

            PathVariable pathVar = param.getAnnotation(PathVariable.class);
            RequestParam requestParam = param.getAnnotation(RequestParam.class);
            RequestHeader requestHeader = param.getAnnotation(RequestHeader.class);
            RequestBody requestBody = param.getAnnotation(RequestBody.class);

            String paramName = getParameterName(param, pathVar, requestParam, requestHeader);
            if (pathVar != null) {
                methodParams.put("path." + paramName, argValue);
                continue;
            }
            if (requestParam != null) {
                methodParams.put("query." + paramName, argValue);
                continue;
            }
            if (requestHeader != null) {
                String headerName = requestHeader.value().isEmpty() ? paramName : requestHeader.value();
                methodParams.put("headers." + headerName, argValue);
                continue;
            }
            if (requestBody != null) {
                methodParams.put("body", convertToMap(argValue));
            }
        }
        return methodParams;
    }

    private String getParameterName(Parameter param,
            PathVariable pathVar,
            RequestParam requestParam,
            RequestHeader requestHeader) {
        if (pathVar != null && !pathVar.value().isEmpty()) {
            return pathVar.value();
        }
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        if (requestHeader != null && !requestHeader.value().isEmpty()) {
            return requestHeader.value();
        }
        return param.getName();
    }

    private Object convertToMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            return value;
        }
        try {
            return objectMapper.convertValue(value, Map.class);
        } catch (Exception ex) {
            log.warn("Failed to convert request body object to map: {}", ex.getMessage());
            return value;
        }
    }

    private Map<String, Object> buildFlowInput(HttpServletRequest request, Map<String, Object> methodParams) {
        Map<String, Object> flowInput = new LinkedHashMap<>();
        Map<String, Object> requestMap = new LinkedHashMap<>();

        requestMap.put("method", request.getMethod());
        requestMap.put("uri", request.getRequestURI());

        Map<String, Object> pathParams = new LinkedHashMap<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        Object body = null;

        for (Map.Entry<String, Object> entry : methodParams.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("path.")) {
                pathParams.put(key.substring(5), entry.getValue());
            } else if (key.startsWith("query.")) {
                queryParams.put(key.substring(6), entry.getValue());
            } else if (key.startsWith("headers.")) {
                headers.put(key.substring(8), entry.getValue());
            } else if ("body".equals(key)) {
                body = entry.getValue();
            }
        }

        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (headers.containsKey(headerName)) {
                    continue;
                }
                List<String> values = Collections.list(request.getHeaders(headerName));
                headers.put(headerName, values.size() == 1 ? values.get(0) : values);
            }
        }

        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String paramName = entry.getKey();
            if (queryParams.containsKey(paramName)) {
                continue;
            }
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                continue;
            }
            queryParams.put(paramName, values.length == 1 ? values[0] : new ArrayList<>(Arrays.asList(values)));
        }

        requestMap.put("path", pathParams);
        requestMap.put("query", queryParams);
        requestMap.put("headers", headers);
        requestMap.put("body", body);
        flowInput.put("request", requestMap);
        return flowInput;
    }

    private void writeResponse(HttpServletResponse response,
            FlowExecutionResult result,
            RestEntryPoint entryPoint) throws Exception {
        Object returnValue = result.getReturnValue();
        if (returnValue == null && result.getContextSnapshot() != null) {
            returnValue = result.getContextSnapshot().get("ret");
        }
        if (returnValue == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        if (returnValue instanceof Map<?, ?> map
                && (map.containsKey("statusCode") || map.containsKey("headers") || map.containsKey("body"))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseEntity = (Map<String, Object>) returnValue;
            writeResponseEntity(response, responseEntity, entryPoint);
            return;
        }

        writeNormalResponse(response, returnValue, entryPoint);
    }

    private void writeResponseEntity(HttpServletResponse response,
            Map<String, Object> responseEntity,
            RestEntryPoint entryPoint) throws Exception {
        int statusCode = parseStatus(responseEntity.get("statusCode"), HttpServletResponse.SC_OK);
        response.setStatus(statusCode);

        Object headersObj = responseEntity.get("headers");
        if (headersObj instanceof Map<?, ?> headers) {
            for (Map.Entry<?, ?> item : headers.entrySet()) {
                String headerName = String.valueOf(item.getKey());
                Object headerValue = item.getValue();
                if (headerValue == null) {
                    continue;
                }
                if (headerValue instanceof List<?> list) {
                    for (Object value : list) {
                        response.addHeader(headerName, String.valueOf(value));
                    }
                    continue;
                }
                if (headerValue.getClass().isArray()) {
                    int length = Array.getLength(headerValue);
                    for (int i = 0; i < length; i++) {
                        response.addHeader(headerName, String.valueOf(Array.get(headerValue, i)));
                    }
                    continue;
                }
                response.setHeader(headerName, String.valueOf(headerValue));
            }
        }

        Object body = responseEntity.get("body");
        if (body == null) {
            if (!response.containsHeader("Content-Type")) {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            }
            return;
        }
        if (!response.containsHeader("Content-Type")) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        writeResponseBody(response, body);
    }

    private void writeNormalResponse(HttpServletResponse response,
            Object returnValue,
            RestEntryPoint entryPoint) throws Exception {
        ErrorResponseFormat format = getErrorResponseFormat(entryPoint);
        if (format != null && format.getErrorCodeField() != null && !format.getErrorCodeField().isBlank()) {
            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put(format.getErrorCodeField(), format.getSuccessCode());

            Object filteredValue = filterNonSerializableObjects(returnValue);
            String detailsField = format.getDetailsField();
            if (detailsField != null && !detailsField.isBlank()) {
                responseBody.put(detailsField, filteredValue);
            } else {
                responseBody.put("data", filteredValue);
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), responseBody);
            return;
        }

        Object filteredValue = filterNonSerializableObjects(returnValue);
        if (filteredValue instanceof String || filteredValue instanceof Number || filteredValue instanceof Boolean) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
            response.getWriter().write(String.valueOf(filteredValue));
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        writeResponseBody(response, filteredValue);
    }

    private void writeInboundError(HttpServletResponse response, InboundInterceptorException exception) {
        try {
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(exception.getStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), exception.getBody());
        } catch (Exception ex) {
            log.error("Failed to write inbound interceptor error", ex);
        }
    }

    private void writeValidationError(HttpServletResponse response,
            ValidationException validationException,
            RestEntryPoint entryPoint) {
        try {
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ErrorResponseFormat format = getErrorResponseFormat(entryPoint);
            Map<String, Object> errorBody = validationException.toErrorBody(format);
            objectMapper.writeValue(response.getWriter(), errorBody);
        } catch (Exception ex) {
            log.error("Failed to write validation error", ex);
        }
    }

    private void writeGeneralError(HttpServletResponse response,
            Exception exception,
            RestEntryPoint entryPoint) {
        try {
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ErrorResponseFormat format = getErrorResponseFormat(entryPoint);
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put(format.getErrorCodeField(), format.getDefaultErrorCode());
            String errorMessage = exception.getMessage();
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "Flow execution failed";
            }
            errorBody.put(format.getErrorMessageField(), errorMessage);

            if (format.getDetailsField() != null && !format.getDetailsField().isBlank()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("exceptionType", exception.getClass().getName());
                details.put("message", exception.getMessage());
                errorBody.put(format.getDetailsField(), details);
            }

            objectMapper.writeValue(response.getWriter(), errorBody);
        } catch (Exception ex) {
            log.error("Failed to write general error", ex);
        }
    }

    private ErrorResponseFormat getErrorResponseFormat(RestEntryPoint entryPoint) {
        return entryPoint == null ? ErrorResponseFormat.STANDARD : entryPoint.getErrorResponseFormat();
    }

    private void writeResponseBody(HttpServletResponse response, Object body) throws Exception {
        if (body == null) {
            return;
        }
        Object filtered = filterNonSerializableObjects(body);
        if (filtered instanceof String || filtered instanceof Number || filtered instanceof Boolean) {
            response.getWriter().write(String.valueOf(filtered));
            return;
        }
        objectMapper.writeValue(response.getWriter(), filtered);
    }

    private Object filterNonSerializableObjects(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Map<?, ?> map) {
            Map<Object, Object> filtered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> item : map.entrySet()) {
                filtered.put(item.getKey(), filterNonSerializableObjects(item.getValue()));
            }
            return filtered;
        }
        if (obj instanceof List<?> list) {
            List<Object> filtered = new ArrayList<>(list.size());
            for (Object item : list) {
                filtered.add(filterNonSerializableObjects(item));
            }
            return filtered;
        }
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            List<Object> filtered = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                filtered.add(filterNonSerializableObjects(Array.get(obj, i)));
            }
            return filtered;
        }
        return obj;
    }

    private int parseStatus(Object status, int fallback) {
        if (status == null) {
            return fallback;
        }
        if (status instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(status));
        } catch (Exception ex) {
            return fallback;
        }
    }
}

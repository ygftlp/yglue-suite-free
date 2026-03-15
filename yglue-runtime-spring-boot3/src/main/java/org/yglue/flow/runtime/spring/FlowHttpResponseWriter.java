package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.definition.ErrorResponseFormat;
import org.yglue.flow.runtime.core.definition.RestEntryPoint;
import org.yglue.flow.runtime.core.validator.ValidationException;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class FlowHttpResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(FlowHttpResponseWriter.class);

    private final ObjectMapper objectMapper;

    FlowHttpResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void writeFlowResult(HttpServletResponse response, FlowExecutionResult result, RestEntryPoint entryPoint) throws Exception {
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
            writeResponseEntity(response, responseEntity);
            return;
        }

        writeNormalResponse(response, returnValue, entryPoint);
    }

    void writeInboundError(HttpServletResponse response, InboundInterceptorException exception) {
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

    void writeValidationError(HttpServletResponse response, ValidationException validationException, RestEntryPoint entryPoint) {
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

    void writeGeneralError(HttpServletResponse response, Exception exception, RestEntryPoint entryPoint) {
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

    private void writeResponseEntity(HttpServletResponse response, Map<String, Object> responseEntity) throws Exception {
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

    private void writeNormalResponse(HttpServletResponse response, Object returnValue, RestEntryPoint entryPoint) throws Exception {
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

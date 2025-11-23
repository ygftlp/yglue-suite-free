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
import org.yglue.flow.runtime.core.validator.ValidationException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AOP 切面：拦截所有 REST 方法
 * 根据 REST 请求查找流程规则，如果存在则执行流程，否则执行原方法
 * 在方法执行时获取 Spring 已经绑定好的方法参数，传递给流程引擎
 */
@Aspect
@Order(1) // 确保在方法执行前拦截
public class FlowOrchestratedAspect {

    private static final Logger log = LoggerFactory.getLogger(FlowOrchestratedAspect.class);

    private final RuleEngine ruleEngine;
    private final ObjectMapper objectMapper;
    private final RequestSchemaValidator requestSchemaValidator;
    private final RestEntryPointRegistry entryPointRegistry;

    public FlowOrchestratedAspect(RuleEngine ruleEngine,
                                  ObjectMapper objectMapper,
                                  RequestSchemaValidator requestSchemaValidator,
                                  RestEntryPointRegistry entryPointRegistry) {
        this.ruleEngine = ruleEngine;
        this.objectMapper = objectMapper;
        this.requestSchemaValidator = requestSchemaValidator;
        this.entryPointRegistry = entryPointRegistry;
    }

    /**
     * 拦截所有带 Spring Web 映射注解的方法（@RequestMapping, @GetMapping, @PostMapping 等）
     */
    @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping)")
    public Object interceptRestMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取 HttpServletRequest 和 HttpServletResponse
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            // 没有请求上下文，执行原方法
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        
        if (response == null) {
            // 没有响应对象，执行原方法
            return joinPoint.proceed();
        }

        // 根据 REST 请求查找对应的流程规则
        String method = request.getMethod();
        String path = request.getRequestURI();
        java.util.Optional<org.yglue.flow.runtime.core.definition.RestEntryPoint> entryPointOpt = 
            entryPointRegistry.findMatch(method, path);
        
        if (entryPointOpt.isEmpty()) {
            // 没有找到匹配的流程规则，执行原方法
            if (log.isDebugEnabled()) {
                log.debug("No flow rule found for {} {}, proceeding with original method", method, path);
            }
            return joinPoint.proceed();
        }

        // 找到匹配的流程规则，执行流程
        org.yglue.flow.runtime.core.definition.RestEntryPoint entryPoint = entryPointOpt.get();
        String ruleId = entryPoint.getFlowCode();
        
        if (log.isDebugEnabled()) {
            log.debug("Found flow rule for {} {} -> ruleId={}", method, path, ruleId);
        }

        // 获取方法的所有参数（Spring 已经完成绑定）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method methodObj = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = methodObj.getParameters();
        
        // 构建方法参数 Map
        Map<String, Object> methodParams = extractMethodParameters(parameters, args);
        
        // 构建流程输入（包含 request 对象和方法参数）
        Map<String, Object> flowInput = buildFlowInput(request, methodParams);
        
        try {
            // 执行流程
            FlowExecutionResult result = ruleEngine.execute(ruleId, flowInput);
            
            if (log.isInfoEnabled()) {
                log.info("yglue flow executed for ruleId={} {} {}", ruleId, method, path);
            }
            
            // 将流程结果写入响应
            writeResponse(response, result, entryPoint);
            
            // 不执行原方法，直接返回 null（因为响应已经写入）
            return null;
        } catch (ValidationException e) {
            // 校验异常：返回统一的错误响应格式
            log.warn("Validation failed for ruleId={} {} {}: {}", ruleId, method, path, e.getMessage());
            writeValidationError(response, e, entryPoint);
            return null;
        } catch (Exception e) {
            // 其他异常：统一处理并返回错误响应给前端，不抛出给 Spring 异常处理器
            log.error("Flow execution failed for ruleId={} {} {}", ruleId, method, path, e);
            writeGeneralError(response, e, entryPoint);
            return null;
        }
    }

    /**
     * 提取方法参数，根据注解类型进行分类
     */
    private Map<String, Object> extractMethodParameters(Parameter[] parameters, Object[] args) {
        Map<String, Object> methodParams = new LinkedHashMap<>();
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Object argValue = args[i];
            
            // 检查参数上的注解
            PathVariable pathVar = param.getAnnotation(PathVariable.class);
            RequestParam requestParam = param.getAnnotation(RequestParam.class);
            RequestHeader requestHeader = param.getAnnotation(RequestHeader.class);
            RequestBody requestBody = param.getAnnotation(RequestBody.class);
            
            String paramName = getParameterName(param, pathVar, requestParam, requestHeader);
            
            if (pathVar != null) {
                // 路径变量
                methodParams.put("path." + paramName, argValue);
            } else if (requestParam != null) {
                // 查询参数
                methodParams.put("query." + paramName, argValue);
            } else if (requestHeader != null) {
                // 请求头
                String headerName = requestHeader.value().isEmpty() ? paramName : requestHeader.value();
                methodParams.put("headers." + headerName, argValue);
            } else if (requestBody != null) {
                // 请求体
                methodParams.put("body", convertToMap(argValue));
            } else {
                // 没有注解的参数，可能是其他类型（如 HttpServletRequest, HttpServletResponse 等）
                // 这些参数不传递给流程，跳过
                continue;
            }
        }
        
        return methodParams;
    }

    /**
     * 获取参数名称
     */
    private String getParameterName(Parameter param, PathVariable pathVar, 
                                    RequestParam requestParam, RequestHeader requestHeader) {
        // 优先使用注解中的 value
        if (pathVar != null && !pathVar.value().isEmpty()) {
            return pathVar.value();
        }
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        if (requestHeader != null && !requestHeader.value().isEmpty()) {
            return requestHeader.value();
        }
        // 否则使用参数名
        return param.getName();
    }

    /**
     * 将对象转换为 Map（用于请求体）
     */
    private Object convertToMap(Object value) {
        if (value == null) {
            return null;
        }
        // 如果已经是 Map，直接返回
        if (value instanceof Map) {
            return value;
        }
        // 使用 Jackson 转换为 Map
        try {
            return objectMapper.convertValue(value, Map.class);
        } catch (Exception e) {
            log.warn("Failed to convert object to Map: {}", e.getMessage());
            // 如果转换失败，尝试序列化再反序列化
            try {
                String json = objectMapper.writeValueAsString(value);
                return objectMapper.readValue(json, Map.class);
            } catch (Exception ex) {
                log.warn("Failed to serialize/deserialize object: {}", ex.getMessage());
                return value;
            }
        }
    }

    /**
     * 构建流程输入
     */
    private Map<String, Object> buildFlowInput(HttpServletRequest request, Map<String, Object> methodParams) {
        Map<String, Object> flowInput = new LinkedHashMap<>();
        Map<String, Object> requestMap = new LinkedHashMap<>();
        
        // 基本信息
        requestMap.put("method", request.getMethod());
        requestMap.put("uri", request.getRequestURI());
        
        // 从 methodParams 中提取分类的参数
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
        
        // 补充从 HttpServletRequest 获取的请求头（如果方法参数中没有）
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                // 如果方法参数中已经有这个请求头，跳过（方法参数优先）
                if (!headers.containsKey(headerName)) {
                    java.util.Enumeration<String> headerValues = request.getHeaders(headerName);
                    java.util.List<String> values = headerValues == null ? 
                        java.util.List.of() : java.util.Collections.list(headerValues);
                    headers.put(headerName, values.size() == 1 ? values.get(0) : values);
                }
            }
        }
        
        // 补充从 HttpServletRequest 获取的查询参数（如果方法参数中没有）
        java.util.Map<String, String[]> parameterMap = request.getParameterMap();
        for (java.util.Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String paramName = entry.getKey();
            // 如果方法参数中已经有这个查询参数，跳过（方法参数优先）
            if (!queryParams.containsKey(paramName)) {
                String[] values = entry.getValue();
                if (values != null && values.length == 1) {
                    queryParams.put(paramName, values[0]);
                } else if (values != null) {
                    queryParams.put(paramName, java.util.List.of(values));
                }
            }
        }
        
        requestMap.put("path", pathParams);
        requestMap.put("query", queryParams);
        requestMap.put("headers", headers);
        requestMap.put("body", body);
        
        // 将 request 对象放在 flowInput 中
        flowInput.put("request", requestMap);
        
        return flowInput;
    }

    /**
     * 将流程执行结果写入响应
     * <p>
     * 支持以下响应类型：
     * 1. ResponseEntity 结构（Map 格式，包含 statusCode、headers、body）
     * 2. 单值响应（String、Number、Boolean、Map、自定义对象）
     * 3. 多值响应（List、Array）
     * </p>
     * 
     * @param response HTTP 响应对象
     * @param result 流程执行结果
     * @param entryPoint REST 入口点配置
     */
    private void writeResponse(HttpServletResponse response, 
                               FlowExecutionResult result,
                               org.yglue.flow.runtime.core.definition.RestEntryPoint entryPoint) throws Exception {
        // 优先使用流程的返回值，如果没有则尝试从上下文中获取最后一个节点的结果
        Object returnValue = result.getReturnValue();
        if (returnValue == null) {
            // 尝试从上下文快照中获取最后一个节点的结果
            Map<String, Object> contextSnapshot = result.getContextSnapshot();
            if (contextSnapshot != null) {
                returnValue = contextSnapshot.get("_lastNodeResult");
            }
        }
        
        if (returnValue == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        
        // 检查是否是 ResponseEntity 结构（Map 格式，包含 statusCode、headers、body）
        if (returnValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = (Map<String, Object>) returnValue;
            
            // 检查是否包含 ResponseEntity 结构的标识字段
            if (responseMap.containsKey("statusCode") || 
                responseMap.containsKey("headers") || 
                responseMap.containsKey("body")) {
                // 处理 ResponseEntity 结构
                writeResponseEntity(response, responseMap, entryPoint);
                return;
            }
        }
        
        // 处理普通响应（单值或多值）
        writeNormalResponse(response, returnValue, entryPoint);
    }
    
    /**
     * 写入 ResponseEntity 结构的响应
     * 
     * @param response HTTP 响应对象
     * @param responseEntity ResponseEntity 结构（Map 格式）
     */
    private void writeResponseEntity(HttpServletResponse response, Map<String, Object> responseEntity,
                                    org.yglue.flow.runtime.core.definition.RestEntryPoint entryPoint) throws Exception {
        // 获取状态码（默认 200）
        Integer statusCode = (Integer) responseEntity.getOrDefault("statusCode", HttpServletResponse.SC_OK);
        response.setStatus(statusCode);
        
        // 设置响应头
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) responseEntity.get("headers");
        if (headers != null) {
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                String headerName = entry.getKey();
                Object headerValue = entry.getValue();
                if (headerValue != null) {
                    // 如果值是 List，设置多个值
                    if (headerValue instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> values = (java.util.List<Object>) headerValue;
                        for (Object value : values) {
                            response.addHeader(headerName, String.valueOf(value));
                        }
                    } else {
                        response.setHeader(headerName, String.valueOf(headerValue));
                    }
                }
            }
        }
        
        // 设置响应体
        Object body = responseEntity.get("body");
        if (body == null) {
            // 如果没有 body，检查 Content-Type 是否已设置
            if (!response.containsHeader("Content-Type")) {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            }
            return;
        }
        
        // 如果 Content-Type 未设置，默认使用 application/json
        if (!response.containsHeader("Content-Type")) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        
        // 写入响应体
        writeResponseBody(response, body);
    }
    
    /**
     * 写入普通响应（单值或多值）
     * 
     * @param response HTTP 响应对象
     * @param returnValue 返回值（可以是单值或 List）
     * @param entryPoint REST 入口点配置（用于获取数据响应格式）
     */
    private void writeNormalResponse(HttpServletResponse response, Object returnValue,
                                    org.yglue.flow.runtime.core.definition.RestEntryPoint entryPoint) throws Exception {
        // 获取数据响应格式配置
        ErrorResponseFormat format = getErrorResponseFormat(entryPoint);
        
        // 如果配置了数据响应格式，则使用统一格式包装响应
        if (format != null && format.getErrorCodeField() != null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            java.util.Map<String, Object> responseBody = new java.util.LinkedHashMap<>();
            // 成功时使用成功码（默认 1）
            responseBody.put(format.getErrorCodeField(), format.getSuccessCode());
            
            // 如果有 detailsField（通常是 "data"），将返回值放入该字段
            if (format.getDetailsField() != null && !format.getDetailsField().isBlank()) {
                responseBody.put(format.getDetailsField(), returnValue);
            } else {
                // 如果没有 detailsField，将返回值直接放入 data 字段
                responseBody.put("data", returnValue);
            }
            
            objectMapper.writeValue(response.getWriter(), responseBody);
            return;
        }
        
        // 如果没有配置数据响应格式，使用原始响应方式
        // 如果是简单类型，直接写回文本
        if (returnValue instanceof String
                || returnValue instanceof Number
                || returnValue instanceof Boolean) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
            response.getWriter().write(String.valueOf(returnValue));
            return;
        }
        
        // 复杂类型（Map、List、自定义对象等），序列化为 JSON
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        writeResponseBody(response, returnValue);
    }
    
    /**
     * 写入校验错误响应
     *
     * @param response HTTP 响应对象
     * @param validationException 校验异常
     * @param entryPoint REST 入口点配置
     * @throws Exception 如果写入失败
     */
    private void writeValidationError(HttpServletResponse response, 
                                     ValidationException validationException,
                                     org.yglue.flow.runtime.core.definition.RestEntryPoint entryPoint) {
        try {
            // 检查响应是否已提交
            if (response.isCommitted()) {
                log.warn("Response already committed, cannot write validation error response");
                return;
            }
            
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            // 获取错误响应格式配置
            ErrorResponseFormat format = getErrorResponseFormat(entryPoint);
            
            // 转换为指定格式的错误响应
            Map<String, Object> errorBody = validationException.toErrorBody(format);
            objectMapper.writeValue(response.getWriter(), errorBody);
        } catch (Exception e) {
            log.error("Failed to write validation error response", e);
            // 如果写入失败，尝试设置状态码
            if (!response.isCommitted()) {
                try {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                } catch (Exception ex) {
                    log.error("Failed to set validation error status", ex);
                }
            }
        }
    }
    
    /**
     * 写入一般错误响应（非校验异常）
     *
     * @param response HTTP 响应对象
     * @param exception 异常对象
     * @param entryPoint REST 入口点配置
     * @throws Exception 如果写入失败
     */
    private void writeGeneralError(HttpServletResponse response, 
                                   Exception exception,
                                   org.yglue.flow.runtime.core.definition.RestEntryPoint entryPoint) {
        try {
            // 检查响应是否已提交
            if (response.isCommitted()) {
                log.warn("Response already committed, cannot write error response");
                return;
            }
            
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            // 获取错误响应格式配置
            ErrorResponseFormat format = getErrorResponseFormat(entryPoint);
            
            // 构建错误响应体
            Map<String, Object> errorBody = new java.util.LinkedHashMap<>();
            errorBody.put(format.getErrorCodeField(), format.getDefaultErrorCode());
            
            // 获取异常消息
            String errorMessage = exception.getMessage();
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "流程执行失败";
            }
            errorBody.put(format.getErrorMessageField(), errorMessage);
            
            // 如果有 detailsField，添加异常详情
            if (format.getDetailsField() != null && !format.getDetailsField().isBlank()) {
                Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("exceptionType", exception.getClass().getName());
                data.put("message", exception.getMessage());
                
                // 如果是 RuntimeException 且 cause 是 ValidationException，提取详细信息
                if (exception instanceof RuntimeException && exception.getCause() instanceof org.yglue.flow.runtime.core.validator.ValidationException) {
                    org.yglue.flow.runtime.core.validator.ValidationException ve = 
                        (org.yglue.flow.runtime.core.validator.ValidationException) exception.getCause();
                    Map<String, Object> validationData = ve.toErrorBody(format);
                    if (validationData.containsKey(format.getDetailsField())) {
                        data.put("validation", validationData.get(format.getDetailsField()));
                    }
                }
                
                errorBody.put(format.getDetailsField(), data);
            }
            
            objectMapper.writeValue(response.getWriter(), errorBody);
        } catch (Exception e) {
            log.error("Failed to write error response", e);
            // 如果写入失败，尝试设置状态码
            if (!response.isCommitted()) {
                try {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                } catch (Exception ex) {
                    log.error("Failed to set error status", ex);
                }
            }
        }
    }
    
    /**
     * 获取错误响应格式
     * <p>
     * 优先级：
     * 1. entryPoint 中的 errorResponseFormat 配置
     * 2. 默认标准格式
     * </p>
     *
     * @param entryPoint REST 入口点配置
     * @return 错误响应格式
     */
    private ErrorResponseFormat getErrorResponseFormat(org.yglue.flow.runtime.core.definition.RestEntryPoint entryPoint) {
        if (entryPoint == null) {
            return ErrorResponseFormat.STANDARD;
        }
        
        return entryPoint.getErrorResponseFormat();
    }
    
    /**
     * 写入响应体
     * 
     * @param response HTTP 响应对象
     * @param body 响应体内容
     */
    private void writeResponseBody(HttpServletResponse response, Object body) throws Exception {
        if (body == null) {
            return;
        }
        
        // 如果是简单类型，直接写回文本
        if (body instanceof String
                || body instanceof Number
                || body instanceof Boolean) {
            response.getWriter().write(String.valueOf(body));
            return;
        }
        
        // 复杂类型，序列化为 JSON
        objectMapper.writeValue(response.getWriter(), body);
    }
}


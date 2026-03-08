package org.yglue.flow.orch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.FlowEntryPoint;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.persistence.mapper.FlowEntryPointMapper;
import org.yglue.flow.orch.web.dto.flow.request.FlowEntryPointRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FlowEntryPointService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProjectService projectService;
    private final FlowEntryPointMapper mapper;

    public FlowEntryPointService(ProjectService projectService,
                                 FlowEntryPointMapper mapper) {
        this.projectService = projectService;
        this.mapper = mapper;
    }

    @Transactional
    public void replaceEntryPoints(Project project,
                                   String flowCode,
                                   FlowEntryPointRequest request,
                                    String operator) {
        FlowEntryPoint existing = mapper.selectByProjectAndFlow(project.getId(), flowCode);
        if (request == null || request.getPath() == null || request.getPath().isBlank()) {
            if (existing != null && (existing.getDelFlag() == null || existing.getDelFlag() == 0)) {
                mapper.deleteByProjectAndFlow(project.getId(), flowCode, operator);
            }
            return;
        }
        if (existing == null) {
            FlowEntryPoint entry = new FlowEntryPoint();
            entry.setProjectId(project.getId());
            entry.setFlowCode(flowCode);
            entry.setPath(normalizePath(request.getPath()));
            entry.setHttpMethod(normalizeMethod(request.getMethod()));
            entry.setRequestSchemaJson(normalizeSchemaJson(request.getRequestSchemaJson()));
            entry.setDataResponseFormat(normalizeDataResponseFormat(request.getDataResponseFormat()));
            entry.setInboundInterceptorsJson(normalizeInboundInterceptors(request.getInboundInterceptors()));
            entry.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
            entry.setCreateBy(operator);
            entry.setUpdateBy(operator);
            entry.setDelFlag(0);
            mapper.insert(entry);
        } else {
            existing.setPath(normalizePath(request.getPath()));
            existing.setHttpMethod(normalizeMethod(request.getMethod()));
            existing.setRequestSchemaJson(normalizeSchemaJson(request.getRequestSchemaJson()));
            existing.setDataResponseFormat(normalizeDataResponseFormat(request.getDataResponseFormat()));
            existing.setInboundInterceptorsJson(normalizeInboundInterceptors(request.getInboundInterceptors()));
            existing.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
            existing.setUpdateBy(operator);
            existing.setDelFlag(0);
            mapper.update(existing);
        }
    }

    public List<FlowEntryPoint> listByProject(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        return new ArrayList<>(mapper.selectByProject(project.getId()));
    }

    public FlowEntryPoint getByProjectAndFlow(String projectKey, String flowCode) {
        Project project = projectService.requireProject(projectKey);
        FlowEntryPoint entryPoint = mapper.selectByProjectAndFlow(project.getId(), flowCode);
        if (entryPoint != null && (entryPoint.getDelFlag() == null || entryPoint.getDelFlag() == 0)) {
            return entryPoint;
        }
        return null;
    }

    @Transactional
    public FlowEntryPoint updateEnabled(String projectKey,
                                        Long entryPointId,
                                        boolean enabled,
                                        String operator) {
        Project project = projectService.requireProject(projectKey);
        FlowEntryPoint entryPoint = mapper.selectById(entryPointId);
        if (entryPoint == null
                || entryPoint.getDelFlag() != null && entryPoint.getDelFlag() == 1
                || !project.getId().equals(entryPoint.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Entry point not found: " + entryPointId);
        }
        if (entryPoint.getFlowCode() == null || entryPoint.getFlowCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Entry point is not bound to any published flow.");
        }
        boolean current = entryPoint.getEnabled() == null || entryPoint.getEnabled();
        if (current != enabled) {
            int affected = mapper.updateEnabled(entryPointId, enabled, operator);
            if (affected == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Failed to update entry point state, please retry.");
            }
            entryPoint.setEnabled(enabled);
            entryPoint.setUpdateBy(operator);
            entryPoint.setUpdateTime(Instant.now());
        }
        return entryPoint;
    }

    /**
     * 规范化路径
     * <p>
     * 确保路径以 "/" 开头，并移除多余的斜杠。
     * </p>
     * 
     * @param path 原始路径
     * @return 规范化后的路径
     */
    private String normalizePath(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (trimmed.isEmpty()) {
            return "/";
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        trimmed = trimmed.replaceAll("//+", "/");
        // 移除末尾的斜杠（除非是根路径）
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 规范化 HTTP 方法
     * <p>
     * 将方法转换为大写，如果为空则返回 null。
     * </p>
     * 
     * @param method HTTP 方法
     * @return 规范化后的方法（大写），如果为空则返回 null
     */
    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSchemaJson(String rawSchema) {
        if (rawSchema == null) {
            return null;
        }
        String trimmed = rawSchema.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            JsonNode tree = OBJECT_MAPPER.readTree(trimmed);
            return OBJECT_MAPPER.writeValueAsString(tree);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestSchema must be valid JSON", ex);
        }
    }

    /**
     * 规范化数据响应格式
     * <p>
     * 如果为对象则序列化为 JSON 字符串，如果为字符串（预设名称）则直接返回。
     * </p>
     *
     * @param dataResponseFormat 数据响应格式（字符串或对象）
     * @return 规范化后的 JSON 字符串或预设名称
     */
    private String normalizeDataResponseFormat(Object dataResponseFormat) {
        if (dataResponseFormat == null) {
            return null;
        }
        if (dataResponseFormat instanceof String) {
            String str = ((String) dataResponseFormat).trim();
            return str.isEmpty() ? null : str;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(dataResponseFormat);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "dataResponseFormat must be a valid JSON object or preset name", ex);
        }
    }

    private String normalizeInboundInterceptors(Object inboundInterceptors) {
        if (inboundInterceptors == null) {
            return null;
        }
        try {
            JsonNode tree = parseInboundInterceptorsTree(inboundInterceptors);
            validateInboundInterceptorsTree(tree);
            return OBJECT_MAPPER.writeValueAsString(tree);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "inboundInterceptors must be a valid JSON array", ex);
        }
    }

    private JsonNode parseInboundInterceptorsTree(Object inboundInterceptors) throws Exception {
        if (inboundInterceptors instanceof String raw) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                return OBJECT_MAPPER.createArrayNode();
            }
            return OBJECT_MAPPER.readTree(trimmed);
        }
        return OBJECT_MAPPER.valueToTree(inboundInterceptors);
    }

    private void validateInboundInterceptorsTree(JsonNode tree) {
        if (!tree.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "inboundInterceptors must be a JSON array");
        }
        Set<String> codes = new HashSet<>();
        int index = 0;
        for (JsonNode item : tree) {
            if (!item.isObject()) {
                throw badInboundInterceptor("inboundInterceptors[" + index + "] must be an object");
            }

            JsonNode codeNode = item.get("code");
            String code = codeNode == null || codeNode.isNull() ? "" : codeNode.asText("").trim();
            if (code.isEmpty()) {
                throw badInboundInterceptor("inboundInterceptors[" + index + "].code is required");
            }
            if (!codes.add(code)) {
                throw badInboundInterceptor("inboundInterceptors has duplicate code: " + code);
            }

            JsonNode enabledNode = item.get("enabled");
            if (enabledNode != null && !enabledNode.isNull() && !enabledNode.isBoolean()) {
                throw badInboundInterceptor("inboundInterceptors[" + index + "].enabled must be boolean");
            }

            JsonNode orderNode = item.get("order");
            if (orderNode != null && !orderNode.isNull() && !orderNode.isNumber()) {
                throw badInboundInterceptor("inboundInterceptors[" + index + "].order must be number");
            }

            JsonNode configNode = item.get("config");
            if (configNode != null && !configNode.isNull() && !configNode.isObject()) {
                throw badInboundInterceptor("inboundInterceptors[" + index + "].config must be object");
            }

            index++;
        }
    }

    private ResponseStatusException badInboundInterceptor(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

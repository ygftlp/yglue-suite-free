package org.yglue.flow.orch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.Flow;
import org.yglue.flow.orch.domain.ProjectEndpoint;
import org.yglue.flow.orch.domain.FlowVersion;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.persistence.mapper.FlowMapper;
import org.yglue.flow.orch.persistence.mapper.FlowVersionMapper;
import org.yglue.flow.orch.web.dto.flow.request.FlowSaveRequest;
import org.yglue.flow.orch.web.dto.flow.request.FlowPublishRequest;
import org.yglue.flow.orch.web.dto.flow.response.FlowServiceSignatureIssueResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程服务
 * <p>
 * 负责管理流程（Flow）和流程版本（FlowVersion）的创建、查询、发布等操作。
 * 在保存和获取流程版本时会清理 UI 相关字段，只保留核心业务数据。
 * </p>
 * 
 * @author yglue
 * @since 1.0
 */
@Service
public class FlowService {

    /** JSON 对象映射器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 项目服务 */
    private final ProjectService projectService;
    
    /** 流程数据访问对象 */
    private final FlowMapper flowMapper;
    
    /** 流程版本数据访问对象 */
    private final FlowVersionMapper flowVersionMapper;
    
    /** 流程入口点服务 */
    private final FlowEntryPointService flowEntryPointService;
    
    /** 项目端点服务（用于发布前签名校验） */
    private final ProjectEndpointService projectEndpointService;

    /**
     * 构造函数
     * 
     * @param projectService 项目服务，不能为 null
     * @param flowMapper 流程数据访问对象，不能为 null
     * @param flowVersionMapper 流程版本数据访问对象，不能为 null
     * @param flowEntryPointService 流程入口点服务，不能为 null
     */
    public FlowService(ProjectService projectService,
                       FlowMapper flowMapper,
                       FlowVersionMapper flowVersionMapper,
                       FlowEntryPointService flowEntryPointService,
                       ProjectEndpointService projectEndpointService) {
        this.projectService = projectService;
        this.flowMapper = flowMapper;
        this.flowVersionMapper = flowVersionMapper;
        this.flowEntryPointService = flowEntryPointService;
        this.projectEndpointService = projectEndpointService;
    }

    /**
     * 保存流程
     * <p>
     * 如果流程不存在则创建，否则更新流程名称（如果发生变化）。
     * 每次保存都会创建新版本。
     * </p>
     * 
     * @param projectKey 项目标识
     * @param req 保存请求
     * @return 创建的流程版本
     */
    @Transactional
    public FlowVersion saveFlow(String projectKey, FlowSaveRequest req) {
        Project project = projectService.requireProject(projectKey);
        Flow flow = flowMapper.selectByProjectAndCode(project.getId(), req.code);
        if (flow == null) {
            flow = new Flow();
            flow.setProjectId(project.getId());
            flow.setCode(req.code);
            flow.setName(req.name);
            flow.setCreateBy(req.createdBy);
            flow.setUpdateBy(req.createdBy);
            flowMapper.insert(flow);
        } else if (!req.name.equals(flow.getName())) {
            flow.setName(req.name);
            flow.setUpdateBy(req.createdBy);
            flowMapper.update(flow);
        }

        Integer maxVersion = flowVersionMapper.selectMaxVersion(flow.getId());
        int nextVersion = (maxVersion == null || maxVersion < 1) ? 1 : maxVersion + 1;

        FlowVersion version = new FlowVersion();
        version.setFlowId(flow.getId());
        version.setVersionNo(nextVersion);
        version.setContentJson(req.contentJson);
        version.setCreateBy(req.createdBy);
        version.setUpdateBy(req.createdBy);
        flowVersionMapper.insert(version);

        flowMapper.updateLatestVersion(flow.getId(), version.getId());
        flow.setLatestVersionId(version.getId());
        version.setPublished(Boolean.FALSE);

        return version;
    }

    /**
     * 列出项目的所有流程
     * 
     * @param projectKey 项目标识
     * @return 流程列表
     */
    public List<Flow> listFlows(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        return flowMapper.selectByProject(project.getId());
    }

    /**
     * 列出流程的所有版本
     * <p>
     * 会标记每个版本是否为已发布版本。
     * </p>
     * 
     * @param projectKey 项目标识
     * @param code 流程代码
     * @return 流程版本列表（按版本号降序）
     */
    public List<FlowVersion> listVersions(String projectKey, String code) {
        Flow flow = requireFlow(projectKey, code);
        Long publishedId = flow.getPublishedVersionId();
        List<FlowVersion> versions = flowVersionMapper.selectByFlowIdDesc(flow.getId());
        for (FlowVersion version : versions) {
            boolean isPublished = publishedId != null && publishedId.equals(version.getId());
            version.setPublished(isPublished);
        }
        return versions;
    }

    /**
     * 列出项目中“最新版本”存在服务签名失效的流程。
     */
    public List<FlowServiceSignatureIssueResponse> listServiceSignatureIssues(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        List<Flow> flows = flowMapper.selectByProject(project.getId());
        if (flows.isEmpty()) {
            return List.of();
        }
        List<Long> latestIds = flows.stream()
                .map(Flow::getLatestVersionId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (latestIds.isEmpty()) {
            return List.of();
        }
        Map<Long, FlowVersion> latestVersionMap = new HashMap<>();
        for (FlowVersion version : flowVersionMapper.selectByIds(latestIds)) {
            if (version != null && version.getId() != null) {
                latestVersionMap.put(version.getId(), version);
            }
        }
        Set<String> validSignatures = loadValidServiceSignatures(projectKey);
        if (validSignatures.isEmpty()) {
            return List.of();
        }

        List<FlowServiceSignatureIssueResponse> issues = new ArrayList<>();
        for (Flow flow : flows) {
            if (flow.getLatestVersionId() == null) {
                continue;
            }
            FlowVersion version = latestVersionMap.get(flow.getLatestVersionId());
            if (version == null) {
                continue;
            }
            List<String> invalidRefs;
            try {
                invalidRefs = findInvalidServiceRefs(version.getContentJson(), validSignatures);
            } catch (ResponseStatusException ex) {
                invalidRefs = List.of("Flow content JSON 无法解析：" + ex.getReason());
            }
            if (invalidRefs.isEmpty()) {
                continue;
            }
            FlowServiceSignatureIssueResponse item = new FlowServiceSignatureIssueResponse();
            item.setFlowCode(flow.getCode());
            item.setFlowName(flow.getName());
            item.setVersionId(version.getId());
            item.setVersionNo(version.getVersionNo());
            item.setIssueCount(invalidRefs.size());
            item.setIssueSamples(invalidRefs.stream().limit(5).collect(Collectors.toList()));
            issues.add(item);
        }

        issues.sort(Comparator
                .comparing(FlowServiceSignatureIssueResponse::getIssueCount, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FlowServiceSignatureIssueResponse::getFlowCode, Comparator.nullsLast(String::compareToIgnoreCase)));
        return issues;
    }

    /**
     * 获取指定版本的流程
     * <p>
     * 会清理 contentJson 中的 UI 相关字段，只保留核心业务数据。
     * </p>
     * 
     * @param projectKey 项目标识
     * @param code 流程代码
     * @param versionNo 版本号
     * @return 流程版本对象
     * @throws ResponseStatusException 如果流程或版本不存在
     */
    public FlowVersion getVersion(String projectKey, String code, Integer versionNo) {
        Flow flow = requireFlow(projectKey, code);
        FlowVersion version = flowVersionMapper.selectByFlowIdAndVersion(flow.getId(), versionNo);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flow version not found: " + code + "@" + versionNo);
        }
        boolean isPublished = flow.getPublishedVersionId() != null
                && flow.getPublishedVersionId().equals(version.getId());
        version.setPublished(isPublished);
        
        // 清理 contentJson 中的 UI 相关字段，只保留核心业务数据
        String cleanedContentJson = cleanContentJson(version.getContentJson());
        version.setContentJson(cleanedContentJson);
        
        return version;
    }
    
    /**
     * 清理 contentJson，移除 UI 相关字段，只保留核心业务数据（节点、边、设置、版本信息）
     * <p>
     * 注意：position 字段会被保留，因为它是 Vue Flow 渲染节点所必需的。
     * </p>
     */
    private String cleanContentJson(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return contentJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(contentJson);
            if (!root.isObject()) {
                return contentJson;
            }
            
            ObjectNode cleaned = OBJECT_MAPPER.createObjectNode();
            
            // 清理节点数组
            JsonNode nodes = root.get("nodes");
            if (nodes != null && nodes.isArray()) {
                ArrayNode cleanedNodes = OBJECT_MAPPER.createArrayNode();
                for (JsonNode node : nodes) {
                    if (node.isObject()) {
                        ObjectNode cleanedNode = cleanNode((ObjectNode) node);
                        cleanedNodes.add(cleanedNode);
                    }
                }
                cleaned.set("nodes", cleanedNodes);
            }
            
            // 清理边数组
            JsonNode edges = root.get("edges");
            if (edges != null && edges.isArray()) {
                ArrayNode cleanedEdges = OBJECT_MAPPER.createArrayNode();
                for (JsonNode edge : edges) {
                    if (edge.isObject()) {
                        ObjectNode cleanedEdge = cleanEdge((ObjectNode) edge);
                        cleanedEdges.add(cleanedEdge);
                    }
                }
                cleaned.set("edges", cleanedEdges);
            }
            
            // 保留 settings（但移除 entrypoint，因为它单独存储）
            JsonNode settings = root.get("settings");
            if (settings != null && settings.isObject()) {
                ObjectNode cleanedSettings = OBJECT_MAPPER.createObjectNode();
                settings.fields().forEachRemaining(entry -> {
                    if (!"entrypoint".equals(entry.getKey())) {
                        cleanedSettings.set(entry.getKey(), entry.getValue());
                    }
                });
                cleaned.set("settings", cleanedSettings);
            }
            
            return OBJECT_MAPPER.writeValueAsString(cleaned);
        } catch (Exception e) {
            // 如果解析失败，返回原始内容
            return contentJson;
        }
    }
    
    /**
     * 清理节点对象，移除 UI 相关字段
     * <p>
     * 注意：position 字段会被保留，因为它是 Vue Flow 渲染节点所必需的。
     * </p>
     */
    private ObjectNode cleanNode(ObjectNode node) {
        ObjectNode cleaned = OBJECT_MAPPER.createObjectNode();
        
        // UI 相关字段列表（需要过滤掉）
        // 注意：position 不在列表中，因为它是 Vue Flow 渲染节点所必需的，应该被保留
        java.util.Set<String> uiFields = java.util.Set.of(
            "selected", "dragging", "width", "height",
            "style", "class", "className", "resizing", "draggable",
            "connectable", "selectable", "focusable", "deletable"
        );
        
        // 保留核心字段（排除 UI 字段）
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!uiFields.contains(key)) {
                cleaned.set(key, entry.getValue());
            }
        });
        
        return cleaned;
    }
    
    /**
     * 清理边对象，移除 UI 相关字段
     */
    private ObjectNode cleanEdge(ObjectNode edge) {
        ObjectNode cleaned = OBJECT_MAPPER.createObjectNode();
        
        // UI 相关字段列表（需要过滤掉）
        java.util.Set<String> uiFields = java.util.Set.of(
            "sourcePosition", "targetPosition", "selected", "style",
            "class", "className", "labelStyle", "labelBgStyle",
            "markerEnd", "markerStart", "animated", "hidden"
        );
        
        // 保留核心字段（排除 UI 字段）
        edge.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!uiFields.contains(key)) {
                cleaned.set(key, entry.getValue());
            }
        });
        
        return cleaned;
    }

    /**
     * 发布流程版本
     * <p>
     * 将指定版本标记为已发布版本，并更新流程入口点。
     * </p>
     * 
     * @param projectKey 项目标识
     * @param code 流程代码
     * @param req 发布请求
     * @return 发布的流程版本
     * @throws ResponseStatusException 如果流程或版本不存在
     */
    @Transactional
    public FlowVersion publishFlow(String projectKey, String code, FlowPublishRequest req) {
        Project project = projectService.requireProject(projectKey);
        Flow flow = flowMapper.selectByProjectAndCode(project.getId(), code);
        if (flow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow not found: " + code);
        }
        FlowVersion version = flowVersionMapper.selectByFlowIdAndVersion(flow.getId(), req.versionNo);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flow version not found: " + code + "@" + req.versionNo);
        }
        validateServiceMethodSignaturesOnPublish(projectKey, code, version);
        flowMapper.updatePublishedVersion(flow.getId(), version.getId(), req.publishedBy);
        flow.setPublishedVersionId(version.getId());
        version.setPublished(Boolean.TRUE);
        flowEntryPointService.replaceEntryPoints(project, flow.getCode(), req.entrypoint, req.publishedBy);
        return version;
    }

    /**
     * 要求流程存在
     * <p>
     * 如果流程不存在则抛出异常。
     * </p>
     * 
     * @param projectKey 项目标识
     * @param code 流程代码
     * @return 流程对象
     * @throws ResponseStatusException 如果流程不存在
     */
    private Flow requireFlow(String projectKey, String code) {
        Project project = projectService.requireProject(projectKey);
        Flow flow = flowMapper.selectByProjectAndCode(project.getId(), code);
        if (flow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow not found: " + code);
        }
        return flow;
    }

    /**
     * 发布前校验流程中的 serviceRef 是否都能在当前项目服务目录中命中。
     */
    private void validateServiceMethodSignaturesOnPublish(String projectKey, String flowCode, FlowVersion version) {
        Set<String> validSignatures = loadValidServiceSignatures(projectKey);
        if (validSignatures.isEmpty()) {
            return;
        }
        List<String> errors = findInvalidServiceRefs(version.getContentJson(), validSignatures);
        if (!errors.isEmpty()) {
            String first = errors.get(0);
            String suffix = errors.size() > 1 ? "；其余 " + (errors.size() - 1) + " 处同类问题" : "";
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "发布失败：检测到失效的服务签名引用。flow=" + flowCode + "，示例=" + first + suffix
                            + "。请在参数/分支配置中重新选择服务方法后再发布。"
            );
        }
    }

    private List<String> findInvalidServiceRefs(String contentJson, Set<String> validSignatures) {
        if (contentJson == null || contentJson.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(contentJson);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Flow content JSON is invalid, cannot publish: " + ex.getMessage()
            );
        }
        List<String> errors = new ArrayList<>();
        collectInvalidServiceRefs(root, "$", validSignatures, errors);
        return errors;
    }

    private Set<String> loadValidServiceSignatures(String projectKey) {
        Set<String> signatures = new LinkedHashSet<>();
        List<ProjectEndpoint> endpoints = projectEndpointService.list(projectKey);
        for (ProjectEndpoint endpoint : endpoints) {
            if (endpoint == null) {
                continue;
            }
            String endpointType = endpoint.getEndpointType() == null ? "" : endpoint.getEndpointType().trim().toUpperCase(Locale.ROOT);
            if (!"SERVICE".equals(endpointType) && !"FLOW_OPERATION".equals(endpointType)) {
                continue;
            }
            String rawConfig = endpoint.getConfigJson();
            if (rawConfig == null || rawConfig.isBlank()) {
                continue;
            }
            try {
                JsonNode config = OBJECT_MAPPER.readTree(rawConfig);
                if ("SERVICE".equals(endpointType)) {
                    String bean = text(config, "bean");
                    JsonNode operations = config.path("operations");
                    if (operations.isArray()) {
                        for (JsonNode op : operations) {
                            addServiceSignature(signatures, bean, op);
                        }
                    }
                } else {
                    String bean = text(config, "serviceBean");
                    addServiceSignature(signatures, bean, config);
                }
            } catch (Exception ignored) {
                // 非法配置不影响主流程，跳过该端点
            }
        }
        return signatures;
    }

    private void addServiceSignature(Set<String> signatures, String bean, JsonNode op) {
        String serviceBean = bean == null ? "" : bean.trim();
        String methodSignature = text(op, "methodSignature");
        if (methodSignature.isBlank()) {
            methodSignature = buildMethodSignature(op);
        }
        if (!serviceBean.isBlank() && !methodSignature.isBlank()) {
            signatures.add(serviceBean + "|" + methodSignature);
        }
    }

    private String buildMethodSignature(JsonNode op) {
        String methodName = text(op, "method");
        if (methodName.isBlank()) {
            methodName = text(op, "name");
        }
        if (methodName.isBlank()) {
            return "";
        }
        List<String> paramTypes = new ArrayList<>();
        JsonNode params = op.path("params");
        if (params.isArray()) {
            for (JsonNode param : params) {
                String type = text(param, "type");
                if (type.isBlank()) {
                    type = "java.lang.Object";
                }
                paramTypes.add(type);
            }
        }
        return methodName + "(" + String.join(",", paramTypes) + ")";
    }

    private void collectInvalidServiceRefs(JsonNode node, String path, Set<String> validSignatures, List<String> errors) {
        if (node == null || node.isNull()) {
            return;
        }
        if (errors.size() >= 20) {
            return;
        }
        if (node.isObject()) {
            JsonNode serviceRef = node.get("serviceRef");
            if (serviceRef != null && serviceRef.isObject()) {
                String serviceBean = text(serviceRef, "serviceBean");
                String methodSignature = text(serviceRef, "methodSignature");
                if (serviceBean.isBlank() || methodSignature.isBlank()) {
                    errors.add(path + " -> serviceRef 缺少 serviceBean 或 methodSignature");
                } else if (!validSignatures.contains(serviceBean + "|" + methodSignature)) {
                    errors.add(path + " -> " + serviceBean + "." + methodSignature);
                }
            }
            node.fields().forEachRemaining(entry ->
                    collectInvalidServiceRefs(entry.getValue(), path + "." + entry.getKey(), validSignatures, errors));
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i += 1) {
                collectInvalidServiceRefs(node.get(i), path + "[" + i + "]", validSignatures, errors);
                if (errors.size() >= 20) {
                    return;
                }
            }
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return "";
        }
        String value = child.asText();
        return value == null ? "" : value.trim();
    }
}

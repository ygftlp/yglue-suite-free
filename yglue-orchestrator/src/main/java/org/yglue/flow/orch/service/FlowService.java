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
import org.yglue.flow.orch.domain.FlowVersion;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.persistence.mapper.FlowMapper;
import org.yglue.flow.orch.persistence.mapper.FlowVersionMapper;
import org.yglue.flow.orch.web.dto.flow.request.FlowSaveRequest;
import org.yglue.flow.orch.web.dto.flow.request.FlowPublishRequest;

import java.util.List;

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
                       FlowEntryPointService flowEntryPointService) {
        this.projectService = projectService;
        this.flowMapper = flowMapper;
        this.flowVersionMapper = flowVersionMapper;
        this.flowEntryPointService = flowEntryPointService;
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
     * 清理 contentJson，移除所有 UI 相关字段，只保留核心业务数据（节点、边、设置、版本信息）
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
     */
    private ObjectNode cleanNode(ObjectNode node) {
        ObjectNode cleaned = OBJECT_MAPPER.createObjectNode();
        
        // UI 相关字段列表（需要过滤掉）
        java.util.Set<String> uiFields = java.util.Set.of(
            "position", "selected", "dragging", "width", "height",
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
}

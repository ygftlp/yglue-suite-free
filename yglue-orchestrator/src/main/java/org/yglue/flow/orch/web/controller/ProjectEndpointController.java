package org.yglue.flow.orch.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.FlowEntryPoint;
import org.yglue.flow.orch.domain.ProjectEndpoint;
import org.yglue.flow.orch.service.FlowEntryPointService;
import org.yglue.flow.orch.service.ProjectEndpointService;
import org.yglue.flow.orch.web.dto.endpoint.request.ProjectEndpointCreateRequest;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectComponentGroupResponse;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectComponentItemResponse;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectEndpointResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目端点控制器
 * <p>
 * 提供项目端点的 REST API，包括：
 * <ul>
 *   <li>端点列表查询</li>
 *   <li>组件分组查询</li>
 *   <li>端点创建</li>
 *   <li>端点详情查询</li>
 * </ul>
 * </p>
 * 
 * @author yglue
 * @since 1.0
 */
@RestController
@RequestMapping("/api/projects/{projectKey}/endpoints")
public class ProjectEndpointController {

    private static final Logger log = LoggerFactory.getLogger(ProjectEndpointController.class);
    
    /** JSON 对象映射器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 组件类型显示名称映射 */
    private static final Map<String, String> COMPONENT_DISPLAY_NAMES = Map.of(
            "BUSINESS", "业务组件",
            "SYSTEM", "系统组件",
            "VALIDATOR", "自定义校验器组件"
    );

    /** 组件类型排序顺序映射 */
    private static final Map<String, Integer> COMPONENT_ORDER = Map.of(
            "BUSINESS", 0,
            "SYSTEM", 1,
            "VALIDATOR", 2
    );

    /** 项目端点服务 */
    private final ProjectEndpointService endpointService;
    
    /** 流程入口点服务 */
    private final FlowEntryPointService flowEntryPointService;

    /**
     * 构造函数
     * 
     * @param endpointService 项目端点服务，不能为 null
     * @param flowEntryPointService 流程入口点服务，不能为 null
     */
    public ProjectEndpointController(ProjectEndpointService endpointService,
                                     FlowEntryPointService flowEntryPointService) {
        this.endpointService = endpointService;
        this.flowEntryPointService = flowEntryPointService;
    }

    /**
     * 列出项目的所有端点
     * 
     * @param projectKey 项目标识
     * @return 端点响应列表
     */
    @GetMapping
    public List<ProjectEndpointResponse> list(@PathVariable("projectKey") String projectKey) {
        return buildEndpointResponses(projectKey);
    }

    /**
     * 列出项目的所有 REST 端点
     * 
     * @param projectKey 项目标识
     * @return REST 端点响应列表
     */
    @GetMapping("/rests")
    public List<ProjectEndpointResponse> listRests(@PathVariable("projectKey") String projectKey) {
        return buildEndpointResponses(projectKey).stream()
                .filter(endpoint -> "REST".equalsIgnoreCase(endpoint.getEndpointType()))
                .collect(Collectors.toList());
    }

    /**
     * 列出项目的组件分组
     * <p>
     * 按组件类型分组，并按照预定义的顺序排序。
     * </p>
     * 
     * @param projectKey 项目标识
     * @return 组件分组响应列表
     */
    @GetMapping("/components")
    public List<ProjectComponentGroupResponse> listComponents(@PathVariable("projectKey") String projectKey) {
        List<ProjectEndpointResponse> endpoints = buildEndpointResponses(projectKey);
        if (endpoints.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<ProjectEndpointResponse>> grouped = endpoints.stream()
                .collect(Collectors.groupingBy(
                        ep -> resolveComponentType(ep.getComponentType(), ep.getEndpointType())));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<ProjectEndpointResponse> sortedEndpoints = new ArrayList<>(entry.getValue());
                    sortedEndpoints.sort(Comparator.comparing(
                            ep -> StringUtils.hasText(ep.getName()) ? ep.getName() : "",
                            String.CASE_INSENSITIVE_ORDER));

                    List<ProjectComponentItemResponse> items = sortedEndpoints.stream()
                            .map(this::toComponentItem)
                            .collect(Collectors.toList());

                    String typeKey = entry.getKey();
                    String displayName = COMPONENT_DISPLAY_NAMES.getOrDefault(typeKey, typeKey);
                    return new ProjectComponentGroupResponse(typeKey, displayName, items);
                })
                .sorted(Comparator.comparingInt(g ->
                        COMPONENT_ORDER.getOrDefault(g.getType(), Integer.MAX_VALUE)))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定的端点
     * 
     * @param projectKey 项目标识
     * @param endpointId 端点ID
     * @return 端点响应对象
     */
    @GetMapping("/{endpointId}")
    public ProjectEndpointResponse get(@PathVariable("projectKey") String projectKey,
                                       @PathVariable("endpointId") Long endpointId) {
        ProjectEndpoint endpoint = endpointService.get(projectKey, endpointId);
        Map<String, FlowEntryPoint> entryPointMap = loadEntrypointMap(projectKey);
        return toResponse(endpoint, entryPointMap);
    }

    /**
     * 创建端点
     * 
     * @param projectKey 项目标识
     * @param request 创建请求
     * @return 创建后的端点响应对象
     */
    @PostMapping
    public ProjectEndpointResponse create(@PathVariable("projectKey") String projectKey,
                                          @RequestBody @Valid ProjectEndpointCreateRequest request) {
        ProjectEndpoint created = endpointService.create(projectKey, request);
        Map<String, FlowEntryPoint> entryPointMap = loadEntrypointMap(projectKey);
        return toResponse(created, entryPointMap);
    }

    /**
     * 构建端点响应列表
     * 
     * @param projectKey 项目标识
     * @return 端点响应列表
     */
    private List<ProjectEndpointResponse> buildEndpointResponses(String projectKey) {
        List<ProjectEndpoint> endpoints = endpointService.list(projectKey);
        if (endpoints.isEmpty()) {
            return List.of();
        }
        Map<String, FlowEntryPoint> entryPointMap = loadEntrypointMap(projectKey);
        return endpoints.stream()
                .map(endpoint -> toResponse(endpoint, entryPointMap))
                .collect(Collectors.toList());
    }

    /**
     * 将端点响应转换为组件项响应
     * 
     * @param endpoint 端点响应对象
     * @return 组件项响应对象
     */
    private ProjectComponentItemResponse toComponentItem(ProjectEndpointResponse endpoint) {
        ProjectComponentItemResponse item = new ProjectComponentItemResponse();
        item.setId(endpoint.getId());
        item.setName(endpoint.getName());
        item.setDescription(endpoint.getDescription());
        item.setEndpointType(endpoint.getEndpointType());
        item.setComponentType(resolveComponentType(endpoint.getComponentType(), endpoint.getEndpointType()));
        item.setMethod(endpoint.getMethod());
        item.setPath(endpoint.getPath());
        item.setConfigJson(endpoint.getConfigJson());
        item.setCreateTime(endpoint.getCreateTime());
        item.setUpdateTime(endpoint.getUpdateTime());
        item.setEntrypointId(endpoint.getEntrypointId());
        item.setFlowCode(endpoint.getFlowCode());
        item.setEnabled(endpoint.getEnabled());
        item.setReplaceResponse(endpoint.getReplaceResponse());
        return item;
    }

    /**
     * 加载项目的入口点映射
     * <p>
     * 以 "方法|路径" 为键，FlowEntryPoint 为值构建映射。
     * </p>
     * 
     * @param projectKey 项目标识
     * @return 入口点映射
     */
    private Map<String, FlowEntryPoint> loadEntrypointMap(String projectKey) {
        List<FlowEntryPoint> entryPoints = flowEntryPointService.listByProject(projectKey);
        return entryPoints.stream()
                .filter(ep -> composeKey(ep.getHttpMethod(), ep.getPath()) != null)
                .collect(Collectors.toMap(
                        ep -> composeKey(ep.getHttpMethod(), ep.getPath()),
                        ep -> ep,
                        (existing, replacement) -> existing));
    }

    /**
     * 将端点实体转换为响应对象
     * <p>
     * 如果端点是 REST 类型，会关联对应的流程入口点信息。
     * </p>
     * 
     * @param endpoint 端点实体
     * @param entryPointMap 入口点映射
     * @return 端点响应对象
     */
    private ProjectEndpointResponse toResponse(ProjectEndpoint endpoint,
                                               Map<String, FlowEntryPoint> entryPointMap) {
        ProjectEndpointResponse response = ProjectEndpointResponse.from(endpoint);
        enrichSchemaMetadata(endpoint, response);
        
        if ("REST".equalsIgnoreCase(endpoint.getEndpointType())) {
            String key = composeKey(endpoint.getMethod(), endpoint.getPath());
            FlowEntryPoint entryPoint = key != null ? entryPointMap.get(key) : null;
            
            if (entryPoint != null) {
                response.setEntrypointId(entryPoint.getId());
                response.setFlowCode(entryPoint.getFlowCode());
                response.setEnabled(entryPoint.getEnabled() == null || entryPoint.getEnabled());
                response.setReplaceResponse(entryPoint.getReplaceResponse() == null
                        ? Boolean.TRUE
                        : entryPoint.getReplaceResponse());
            } else {
                response.setEnabled(Boolean.FALSE);
                response.setReplaceResponse(Boolean.FALSE);
            }
        }
        
        response.setComponentType(resolveComponentType(response.getComponentType(), response.getEndpointType()));
        return response;
    }

    /**
     * 丰富响应对象的 Schema 元数据
     * <p>
     * 从端点的 configJson 中提取 requestSchemaJson 和 responseSchema。
     * </p>
     * 
     * @param endpoint 端点实体
     * @param response 端点响应对象
     */
    private void enrichSchemaMetadata(ProjectEndpoint endpoint, ProjectEndpointResponse response) {
        String rawConfig = endpoint.getConfigJson();
        if (!StringUtils.hasText(rawConfig)) {
            return;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawConfig);
            JsonNode requestSchemaJsonNode = root.get("requestSchemaJson");
            if (requestSchemaJsonNode != null && !requestSchemaJsonNode.isNull()) {
                response.setRequestSchemaJson(requestSchemaJsonNode.isTextual()
                        ? requestSchemaJsonNode.asText()
                        : requestSchemaJsonNode.toString());
            }

            JsonNode responseSchemaNode = root.get("responseSchema");
            if (responseSchemaNode != null && !responseSchemaNode.isNull()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseSchema =
                        OBJECT_MAPPER.convertValue(responseSchemaNode, Map.class);
                response.setResponseSchema(responseSchema);
            }
        } catch (Exception ex) {
            log.warn("Failed to parse endpoint configJson for schema metadata. endpointId={}, error={}",
                    endpoint.getId(), ex.getMessage());
        }
    }

    /**
     * 组合方法路径键
     * <p>
     * 格式：{method}|{path}
     * </p>
     * 
     * @param method HTTP 方法
     * @param path 路径
     * @return 组合后的键，如果路径为空则返回 null
     */
    private String composeKey(String method, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalizedPath = normalizePath(path);
        String normalizedMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        return normalizedMethod + "|" + normalizedPath;
    }

    /**
     * 标准化路径
     * <p>
     * 对路径进行标准化处理，确保格式统一。
     * </p>
     * 
     * @param path 原始路径
     * @return 标准化后的路径
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
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 解析组件类型
     * <p>
     * 根据组件类型和端点类型确定最终的组件类型。
     * </p>
     * 
     * @param componentType 组件类型，可能为 null
     * @param endpointType 端点类型
     * @return 解析后的组件类型
     */
    private String resolveComponentType(String componentType, String endpointType) {
        if (StringUtils.hasText(componentType)) {
            return componentType.trim().toUpperCase(Locale.ROOT);
        }
        
        if ("REST".equalsIgnoreCase(endpointType)) {
            return "SYSTEM";
        }
        
        if ("SERVICE".equalsIgnoreCase(endpointType)) {
            return "BUSINESS";
        }
        
        return "BUSINESS";
    }
}

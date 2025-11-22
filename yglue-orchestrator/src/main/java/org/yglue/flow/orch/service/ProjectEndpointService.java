package org.yglue.flow.orch.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.ProjectEndpoint;
import org.yglue.flow.orch.persistence.mapper.ProjectEndpointMapper;
import org.yglue.flow.orch.web.dto.endpoint.request.ProjectEndpointCreateRequest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 项目端点服务
 * <p>
 * 负责管理项目端点（ProjectEndpoint）的创建、查询和同步操作。
 * 支持 REST 端点和服务端点的管理。
 * </p>
 * 
 * @author yglue
 * @since 1.0
 */
@Service
public class ProjectEndpointService {

    /** REST 端点类型常量 */
    private static final String REST_TYPE = "REST";
    
    /** 自动同步标识 */
    private static final String AUTO_SYNC = "auto-sync";

    /** 项目服务 */
    private final ProjectService projectService;
    
    /** 项目端点数据访问对象 */
    private final ProjectEndpointMapper endpointMapper;

    /**
     * 构造函数
     * 
     * @param projectService 项目服务，不能为 null
     * @param endpointMapper 项目端点数据访问对象，不能为 null
     */
    public ProjectEndpointService(ProjectService projectService,
                                  ProjectEndpointMapper endpointMapper) {
        this.projectService = projectService;
        this.endpointMapper = endpointMapper;
    }

    /**
     * 列出项目的所有端点
     * 
     * @param projectKey 项目标识
     * @return 端点列表
     */
    public List<ProjectEndpoint> list(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        return endpointMapper.selectByProjectId(project.getId());
    }

    /**
     * 获取指定的端点
     * 
     * @param projectKey 项目标识
     * @param endpointId 端点ID
     * @return 端点对象
     * @throws ResponseStatusException 如果端点不存在或不属于指定项目
     */
    public ProjectEndpoint get(String projectKey, Long endpointId) {
        Project project = projectService.requireProject(projectKey);
        ProjectEndpoint endpoint = endpointMapper.selectById(endpointId);
        if (endpoint == null || !endpoint.getProjectId().equals(project.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Endpoint not found: " + endpointId);
        }
        return endpoint;
    }

    /**
     * 创建或更新端点
     * <p>
     * 如果端点已存在（根据项目ID、端点类型、方法和路径），则更新现有记录；
     * 否则创建新记录。
     * </p>
     * 
     * @param projectKey 项目标识
     * @param request 创建请求
     * @return 创建或更新后的端点对象
     * @throws ResponseStatusException 如果 REST 端点缺少必需的方法或路径
     */
    @Transactional
    public ProjectEndpoint create(String projectKey, ProjectEndpointCreateRequest request) {
        Project project = projectService.requireProject(projectKey);

        String endpointType = determineEndpointType(request.endpointType);
        String componentType = resolveComponentType(request.componentType, endpointType);
        String method = normalize(request.method);
        String path = normalizePath(request.path);

        if (REST_TYPE.equals(endpointType)) {
            if (!StringUtils.hasText(method)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REST endpoint requires method");
            }
            if (!StringUtils.hasText(path)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REST endpoint requires path");
            }
            method = method.toUpperCase();
        }

        ProjectEndpoint existing = endpointMapper.selectByProjectAndKey(project.getId(), endpointType, method, path);
        if (existing != null) {
            // 如果已存在，更新现有记录而不是抛出异常
            existing.setComponentType(componentType);
            existing.setName(request.name.trim());
            existing.setDescription(normalize(request.description));
            existing.setConfigJson(normalize(request.configJson));
            existing.setUpdateBy(normalize(request.createdBy));
            existing.setDelFlag(0);
            endpointMapper.update(existing);
            return existing;
        }

        ProjectEndpoint endpoint = new ProjectEndpoint();
        endpoint.setProjectId(project.getId());
        endpoint.setEndpointType(endpointType);
        endpoint.setComponentType(componentType);
        endpoint.setMethod(method);
        endpoint.setPath(path);
        endpoint.setName(request.name.trim());
        endpoint.setDescription(normalize(request.description));
        endpoint.setConfigJson(normalize(request.configJson));
        endpoint.setCreateBy(normalize(request.createdBy));
        endpoint.setUpdateBy(normalize(request.createdBy));
        endpoint.setDelFlag(0);
        endpointMapper.insert(endpoint);
        return endpoint;
    }

    /**
     * 同步端点列表
     * <p>
     * 将传入的端点列表与数据库中的端点进行同步：
     * <ol>
     *   <li>创建或更新存在的端点</li>
     *   <li>软删除不在列表中的端点</li>
     * </ol>
     * </p>
     * 
     * @param projectId 项目ID
     * @param endpoints 端点负载列表
     * @throws ResponseStatusException 如果 REST 端点缺少必需的方法或路径
     */
    @Transactional
    public void syncEndpoints(Long projectId, List<EndpointPayload> endpoints) {
        List<ProjectEndpoint> existing = endpointMapper.selectByProjectId(projectId);
        Map<String, ProjectEndpoint> existingMap = new HashMap<>();
        for (ProjectEndpoint endpoint : existing) {
            existingMap.put(composeKey(endpoint.getEndpointType(), endpoint.getMethod(), endpoint.getPath()), endpoint);
        }

        Set<Long> retained = new HashSet<>();
        for (EndpointPayload payload : endpoints) {
            String endpointType = determineEndpointType(payload.endpointType());
            String componentType = resolveComponentType(payload.componentType(), endpointType);
            String method = normalize(payload.methodKey());
            String path = normalize(payload.pathKey());

            if (REST_TYPE.equals(endpointType)) {
                if (!StringUtils.hasText(method)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REST endpoint requires method");
                }
                if (!StringUtils.hasText(path)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REST endpoint requires path");
                }
                method = method.toUpperCase();
                path = normalizePath(path);
            }

            String key = composeKey(endpointType, method, path);
            ProjectEndpoint current = existingMap.get(key);
            if (current == null) {
                // 创建新记录或更新现有记录（使用 insertOrUpdate 避免唯一约束冲突）
                ProjectEndpoint endpoint = new ProjectEndpoint();
                endpoint.setProjectId(projectId);
                endpoint.setEndpointType(endpointType);
                endpoint.setComponentType(componentType);
                endpoint.setMethod(method);
                endpoint.setPath(path);
                endpoint.setName(payload.displayName());
                endpoint.setDescription(payload.description());
                endpoint.setConfigJson(payload.configJson());
                endpoint.setCreateBy(AUTO_SYNC);
                endpoint.setUpdateBy(AUTO_SYNC);
                endpoint.setDelFlag(0);
                endpointMapper.insertOrUpdate(endpoint);
                // 插入或更新后，重新查询获取完整记录（包括 ID）
                current = endpointMapper.selectByProjectAndKey(projectId, endpointType, method, path);
                if (current != null) {
                    existingMap.put(key, current);
                }
            } else {
                // 检查是否有变化，如果有变化则更新
                boolean changed = !Objects.equals(current.getEndpointType(), endpointType)
                        || !Objects.equals(normalize(current.getComponentType()), normalize(componentType))
                        || !Objects.equals(current.getMethod(), method)
                        || !Objects.equals(current.getPath(), path)
                        || !Objects.equals(current.getName(), payload.displayName())
                        || !Objects.equals(current.getDescription(), payload.description())
                        || !Objects.equals(current.getConfigJson(), payload.configJson())
                        || current.getDelFlag() == null || current.getDelFlag() != 0;
                if (changed) {
                    current.setEndpointType(endpointType);
                    current.setComponentType(componentType);
                    current.setMethod(method);
                    current.setPath(path);
                    current.setName(payload.displayName());
                    current.setDescription(payload.description());
                    current.setConfigJson(payload.configJson());
                    current.setDelFlag(0);
                    current.setUpdateBy(AUTO_SYNC);
                    endpointMapper.update(current);
                }
            }
            if (current != null) {
                retained.add(current.getId());
            }
        }

        for (ProjectEndpoint endpoint : existing) {
            if (endpoint.getId() != null && !retained.contains(endpoint.getId())) {
                endpointMapper.softDelete(endpoint.getId(), AUTO_SYNC);
            }
        }
    }

    /**
     * 解析组件类型
     * <p>
     * 根据请求的组件类型和端点类型确定最终的组件类型。
     * </p>
     * 
     * @param requested 请求的组件类型，可能为 null
     * @param endpointType 端点类型
     * @return 解析后的组件类型
     */
    private String resolveComponentType(String requested, String endpointType) {
        if (StringUtils.hasText(requested)) {
            return requested.trim().toUpperCase();
        }
        if (REST_TYPE.equals(endpointType)) {
            return "SYSTEM";
        }
        if ("SERVICE".equalsIgnoreCase(endpointType)) {
            return "BUSINESS";
        }
        return "BUSINESS";
    }

    /**
     * 组合端点唯一键
     * <p>
     * 使用端点类型、方法和路径组合成唯一键，格式：{type}|{method}|{path}
     * </p>
     * 
     * @param type 端点类型
     * @param method 方法
     * @param path 路径
     * @return 组合后的唯一键
     */
    private static String composeKey(String type, String method, String path) {
        String keyType = type == null ? "" : type;
        String keyMethod = method == null ? "" : method;
        String keyPath = path == null ? "" : path;
        return keyType + "|" + keyMethod + "|" + keyPath;
    }

    /**
     * 确定端点类型
     * <p>
     * 如果类型为空，默认返回 REST 类型。
     * </p>
     * 
     * @param type 端点类型，可能为 null
     * @return 标准化后的端点类型
     */
    private String determineEndpointType(String type) {
        if (!StringUtils.hasText(type)) {
            return REST_TYPE;
        }
        return type.trim().toUpperCase();
    }

    /**
     * 标准化字符串值
     * <p>
     * 如果值不为空，则去除首尾空格；否则返回 null。
     * </p>
     * 
     * @param value 原始值
     * @return 标准化后的值，如果原始值为空则返回 null
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 标准化路径
     * <p>
     * 对路径进行标准化处理：
     * <ul>
     *   <li>如果路径为空，返回 "/"</li>
     *   <li>确保路径以 "/" 开头</li>
     *   <li>移除多余的连续斜杠</li>
     *   <li>移除末尾的斜杠（根路径 "/" 除外）</li>
     * </ul>
     * </p>
     * 
     * @param path 原始路径
     * @return 标准化后的路径
     */
    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String trimmed = path.trim();
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
     * 端点负载记录
     * <p>
     * 用于同步端点时的数据传输对象。
     * </p>
     * 
     * @param endpointType 端点类型
     * @param componentType 组件类型
     * @param methodKey 方法键
     * @param pathKey 路径键
     * @param displayName 显示名称
     * @param description 描述
     * @param configJson 配置 JSON
     */
    public record EndpointPayload(String endpointType,
                                   String componentType,
                                   String methodKey,
                                   String pathKey,
                                   String displayName,
                                   String description,
                                   String configJson) {}
}

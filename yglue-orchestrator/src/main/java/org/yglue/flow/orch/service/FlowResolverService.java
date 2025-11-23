package org.yglue.flow.orch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yglue.flow.orch.domain.FlowResolver;
import org.yglue.flow.orch.persistence.mapper.FlowResolverMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 流程解析器服务类
 * <p>
 * 提供流程解析器的同步、查询等功能。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowResolverService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FlowResolverMapper flowResolverMapper;

    /**
     * 同步流程解析器
     * <p>
     * 根据提供的解析器载荷列表，同步更新项目下的流程解析器：
     * - 如果解析器不存在，则创建
     * - 如果解析器已存在，则更新（仅当字段发生变化时）
     * - 不在载荷列表中的解析器将被标记为已删除
     * </p>
     *
     * @param projectId 项目ID
     * @param payloads 解析器载荷列表
     */
    @Transactional
    public void syncResolvers(Long projectId, Collection<ResolverPayload> payloads) {
        Map<String, FlowResolver> existing = flowResolverMapper.selectByProjectId(projectId)
                .stream()
                .collect(Collectors.toMap(FlowResolver::getType, r -> r, (a, b) -> a, HashMap::new));

        List<String> retainedTypes = new ArrayList<>();
        for (ResolverPayload payload : payloads) {
            if (payload.type() == null || payload.type().isBlank()) {
                continue;
            }
            String type = payload.type();
            retainedTypes.add(type);
            FlowResolver current = existing.get(type);
            if (current == null) {
                FlowResolver resolver = new FlowResolver();
                resolver.setProjectId(projectId);
                resolver.setType(type);
                resolver.setName(defaultIfBlank(payload.name(), type));
                resolver.setDescription(payload.description());
                resolver.setCategory(payload.category());
                resolver.setBuiltin(Boolean.TRUE.equals(payload.builtin()) ? 1 : 0);
                resolver.setConfigSchema(payload.configSchema());
                resolver.setClassName(payload.className());
                resolver.setRawJson(payload.rawJson());
                resolver.setDelFlag(0);
                flowResolverMapper.insert(resolver);
            } else {
                boolean changed = false;
                if (!Objects.equals(current.getName(), defaultIfBlank(payload.name(), type))) {
                    current.setName(defaultIfBlank(payload.name(), type));
                    changed = true;
                }
                if (!Objects.equals(current.getDescription(), payload.description())) {
                    current.setDescription(payload.description());
                    changed = true;
                }
                if (!Objects.equals(current.getCategory(), payload.category())) {
                    current.setCategory(payload.category());
                    changed = true;
                }
                int builtinFlag = Boolean.TRUE.equals(payload.builtin()) ? 1 : 0;
                if (!Objects.equals(current.getBuiltin(), builtinFlag)) {
                    current.setBuiltin(builtinFlag);
                    changed = true;
                }
                if (!Objects.equals(current.getConfigSchema(), payload.configSchema())) {
                    current.setConfigSchema(payload.configSchema());
                    changed = true;
                }
                if (!Objects.equals(current.getClassName(), payload.className())) {
                    current.setClassName(payload.className());
                    changed = true;
                }
                if (!Objects.equals(current.getRawJson(), payload.rawJson())) {
                    current.setRawJson(payload.rawJson());
                    changed = true;
                }
                if (current.getDelFlag() == null || current.getDelFlag() != 0) {
                    current.setDelFlag(0);
                    changed = true;
                }
                if (changed) {
                    flowResolverMapper.update(current);
                }
            }
        }

        flowResolverMapper.markDeletedByProjectExcludingTypes(projectId, retainedTypes);
    }

    /**
     * 查询项目的所有活跃流程解析器
     * <p>
     * 如果项目下没有自定义解析器，则返回内置解析器列表。
     * </p>
     *
     * @param projectId 项目ID
     * @return 流程解析器列表
     */
    public List<FlowResolver> listActive(Long projectId) {
        List<FlowResolver> resolvers = flowResolverMapper.selectActiveByProjectId(projectId);
        if (resolvers == null || resolvers.isEmpty()) {
            return builtinResolvers();
        }
        return resolvers;
    }

    /**
     * 如果值为空则返回默认值
     *
     * @param value 值
     * @param fallback 默认值
     * @return 值或默认值
     */
    private static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * 流程解析器数据载荷
     *
     * @param type 解析器类型
     * @param name 解析器名称
     * @param description 描述
     * @param category 分类
     * @param builtin 是否内置
     * @param configSchema 配置 Schema
     * @param className 类名
     * @param rawJson 原始 JSON
     */
    public record ResolverPayload(
            String type,
            String name,
            String description,
            String category,
            Boolean builtin,
            String configSchema,
            String className,
            String rawJson
    ) {
        /**
         * 从原始 Map 创建解析器载荷
         *
         * @param node 原始节点 Map
         * @return 解析器载荷，如果节点无效则返回 null
         */
        public static ResolverPayload fromRaw(Map<String, Object> node) {
            if (node == null) {
                return null;
            }
            String type = stringValue(node.get("type"));
            if (type == null || type.isBlank()) {
                return null;
            }
            return new ResolverPayload(
                    type,
                    stringValue(node.get("name")),
                    stringValue(node.get("description")),
                    stringValue(node.get("category")),
                    booleanValue(node.get("builtin")),
                    stringValue(node.get("configSchema")),
                    stringValue(node.get("class")),
                    toJson(node)
            );
        }

        /**
         * 将 Map 序列化为 JSON 字符串
         *
         * @param node 节点 Map
         * @return JSON 字符串，如果序列化失败则返回 null
         */
        @SuppressWarnings("unchecked")
        private static String toJson(Map<String, Object> node) {
            try {
                return OBJECT_MAPPER.writeValueAsString(node);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize resolver payload: {}", e.getMessage());
                return null;
            }
        }

        /**
         * 将对象转换为字符串
         *
         * @param raw 原始对象
         * @return 字符串，如果对象为 null 则返回 null
         */
        private static String stringValue(Object raw) {
            return raw == null ? null : String.valueOf(raw);
        }

        /**
         * 将对象转换为布尔值
         *
         * @param raw 原始对象
         * @return 布尔值，如果对象为 null 则返回 null
         */
        private static Boolean booleanValue(Object raw) {
            if (raw == null) return null;
            if (raw instanceof Boolean b) return b;
            return Boolean.parseBoolean(raw.toString());
        }
    }

    /**
     * 获取内置解析器列表
     *
     * @return 内置解析器列表
     */
    private static List<FlowResolver> builtinResolvers() {
        List<FlowResolver> list = new ArrayList<>();
        list.add(buildBuiltin("REQUEST", "请求参数", "从 HTTP 请求体、路径、查询、Header、Form 等位置提取字段", "HTTP"));
        list.add(buildBuiltin("CONTEXT", "流程上下文", "从流程上下文 ctx 或变量池中读取数据", "上下文"));
        list.add(buildBuiltin("CONSTANT", "常量", "使用固定常量值作为节点输入", "常量"));
        list.add(buildBuiltin("EXPRESSION", "表达式", "通过 SpEL / Groovy 表达式组合请求与上下文数据", "表达式"));
        return list;
    }

    /**
     * 构建内置解析器对象
     *
     * @param type 解析器类型
     * @param name 解析器名称
     * @param description 描述
     * @param category 分类
     * @return 流程解析器对象
     */
    private static FlowResolver buildBuiltin(String type, String name, String description, String category) {
        FlowResolver resolver = new FlowResolver();
        resolver.setType(type);
        resolver.setName(name);
        resolver.setDescription(description);
        resolver.setCategory(category);
        resolver.setBuiltin(1);
        return resolver;
    }
}


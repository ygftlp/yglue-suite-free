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

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowResolverService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FlowResolverMapper flowResolverMapper;

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

    public List<FlowResolver> listActive(Long projectId) {
        List<FlowResolver> resolvers = flowResolverMapper.selectActiveByProjectId(projectId);
        if (resolvers == null || resolvers.isEmpty()) {
            return builtinResolvers();
        }
        return resolvers;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

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

        @SuppressWarnings("unchecked")
        private static String toJson(Map<String, Object> node) {
            try {
                return OBJECT_MAPPER.writeValueAsString(node);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize resolver payload: {}", e.getMessage());
                return null;
            }
        }

        private static String stringValue(Object raw) {
            return raw == null ? null : String.valueOf(raw);
        }

        private static Boolean booleanValue(Object raw) {
            if (raw == null) return null;
            if (raw instanceof Boolean b) return b;
            return Boolean.parseBoolean(raw.toString());
        }
    }

    private static List<FlowResolver> builtinResolvers() {
        List<FlowResolver> list = new ArrayList<>();
        list.add(buildBuiltin("REQUEST", "请求参数", "从 HTTP 请求体、路径、查询、Header、Form 等位置提取字段", "HTTP"));
        list.add(buildBuiltin("CONTEXT", "流程上下文", "从流程上下文 ctx 或变量池中读取数据", "上下文"));
        list.add(buildBuiltin("CONSTANT", "常量", "使用固定常量值作为节点输入", "常量"));
        list.add(buildBuiltin("EXPRESSION", "表达式", "通过 SpEL / Groovy 表达式组合请求与上下文数据", "表达式"));
        return list;
    }

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


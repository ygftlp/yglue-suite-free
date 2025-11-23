package org.yglue.flow.orch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yglue.flow.orch.domain.FlowModel;
import org.yglue.flow.orch.persistence.mapper.FlowModelMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 流程模型服务类
 * <p>
 * 提供流程模型的同步、查询等功能。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
@Service
public class FlowModelService {

    private static final Logger log = LoggerFactory.getLogger(FlowModelService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FlowModelMapper flowModelMapper;

    /**
     * 构造函数
     *
     * @param flowModelMapper 流程模型数据访问对象
     */
    public FlowModelService(FlowModelMapper flowModelMapper) {
        this.flowModelMapper = flowModelMapper;
    }

    /**
     * 同步流程模型
     * <p>
     * 根据提供的模型载荷列表，同步更新项目下的流程模型：
     * - 如果模型不存在，则创建
     * - 如果模型已存在，则更新（仅当字段发生变化时）
     * - 不在载荷列表中的模型将被标记为已删除
     * </p>
     *
     * @param projectId 项目ID
     * @param payloads 模型载荷列表
     */
    @Transactional
    public void syncModels(Long projectId, Collection<ModelPayload> payloads) {
        List<FlowModel> existing = flowModelMapper.selectByProjectId(projectId);
        Map<String, FlowModel> existingByIdentifier = new HashMap<>();
        for (FlowModel model : existing) {
            existingByIdentifier.put(model.getIdentifier(), model);
        }

        List<String> retainedIdentifiers = new ArrayList<>();
        for (ModelPayload payload : payloads) {
            if (payload.identifier() == null || payload.identifier().isBlank()) {
                continue;
            }
            retainedIdentifiers.add(payload.identifier());
            FlowModel current = existingByIdentifier.get(payload.identifier());
            if (current == null) {
                FlowModel toInsert = new FlowModel();
                toInsert.setProjectId(projectId);
                toInsert.setIdentifier(payload.identifier());
                toInsert.setName(defaultIfBlank(payload.name(), payload.identifier()));
                toInsert.setClassName(defaultIfBlank(payload.className(), payload.identifier()));
                toInsert.setDescription(payload.description());
                toInsert.setCategory(payload.category());
                toInsert.setVersion(defaultIfBlank(payload.version(), "1.0.0"));
                toInsert.setTagsJson(writeJsonArray(payload.tags()));
                toInsert.setSchemaJson(payload.schemaJson());
                toInsert.setRawJson(payload.rawJson());
                toInsert.setDelFlag(0);
                flowModelMapper.insert(toInsert);
            } else {
                boolean changed = false;
                if (!Objects.equals(current.getName(), defaultIfBlank(payload.name(), payload.identifier()))) {
                    current.setName(defaultIfBlank(payload.name(), payload.identifier()));
                    changed = true;
                }
                if (!Objects.equals(current.getClassName(), payload.className())) {
                    current.setClassName(payload.className());
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
                if (!Objects.equals(current.getVersion(), defaultIfBlank(payload.version(), "1.0.0"))) {
                    current.setVersion(defaultIfBlank(payload.version(), "1.0.0"));
                    changed = true;
                }
                String tagsJson = writeJsonArray(payload.tags());
                if (!Objects.equals(current.getTagsJson(), tagsJson)) {
                    current.setTagsJson(tagsJson);
                    changed = true;
                }
                if (!Objects.equals(current.getSchemaJson(), payload.schemaJson())) {
                    current.setSchemaJson(payload.schemaJson());
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
                    flowModelMapper.update(current);
                }
            }
        }

        flowModelMapper.markDeletedByProjectExcludingIdentifiers(projectId, retainedIdentifiers);
    }

    /**
     * 查询项目的所有活跃流程模型
     *
     * @param projectId 项目ID
     * @return 流程模型列表
     */
    public List<FlowModel> listActive(Long projectId) {
        return flowModelMapper.selectActiveByProjectId(projectId);
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
     * 将标签列表序列化为 JSON 数组字符串
     *
     * @param tags 标签列表
     * @return JSON 数组字符串，如果列表为空则返回 null
     */
    private static String writeJsonArray(List<String> tags) {
        if (tags == null) {
            return null;
        }
        List<String> normalized = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tags: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 流程模型数据载荷
     *
     * @param identifier 模型标识
     * @param name 模型名称
     * @param className 类名
     * @param description 描述
     * @param category 分类
     * @param version 版本
     * @param tags 标签列表
     * @param schemaJson Schema JSON
     * @param rawJson 原始 JSON
     */
    public record ModelPayload(
            String identifier,
            String name,
            String className,
            String description,
            String category,
            String version,
            List<String> tags,
            String schemaJson,
            String rawJson
    ) {}
}


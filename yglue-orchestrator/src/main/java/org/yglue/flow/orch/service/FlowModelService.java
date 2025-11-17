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

@Service
public class FlowModelService {

    private static final Logger log = LoggerFactory.getLogger(FlowModelService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FlowModelMapper flowModelMapper;

    public FlowModelService(FlowModelMapper flowModelMapper) {
        this.flowModelMapper = flowModelMapper;
    }

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

    public List<FlowModel> listActive(Long projectId) {
        return flowModelMapper.selectActiveByProjectId(projectId);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

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





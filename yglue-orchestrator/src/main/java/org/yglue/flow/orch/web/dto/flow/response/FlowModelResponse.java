package org.yglue.flow.orch.web.dto.flow.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.yglue.flow.orch.domain.FlowModel;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Data
public class FlowModelResponse {
    private Long id;
    private String identifier;
    private String name;
    private String className;
    private String description;
    private String category;
    private String version;
    private List<String> tags;
    private String schemaJson;
    private Date updateTime;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static FlowModelResponse from(FlowModel model) {
        FlowModelResponse resp = new FlowModelResponse();
        resp.setId(model.getId());
        resp.setIdentifier(model.getIdentifier());
        resp.setName(model.getName());
        resp.setClassName(model.getClassName());
        resp.setDescription(model.getDescription());
        resp.setCategory(model.getCategory());
        resp.setVersion(model.getVersion());
        resp.setTags(parseTags(model.getTagsJson()));
        resp.setSchemaJson(model.getSchemaJson());
        resp.setUpdateTime(model.getUpdateTime());
        return resp;
    }

    private static List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(tagsJson, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}






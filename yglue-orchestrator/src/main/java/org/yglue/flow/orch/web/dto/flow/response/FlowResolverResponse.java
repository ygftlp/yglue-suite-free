package org.yglue.flow.orch.web.dto.flow.response;

import lombok.Data;
import org.yglue.flow.orch.domain.FlowResolver;

import java.util.Date;

@Data
public class FlowResolverResponse {
    private Long id;
    private String type;
    private String name;
    private String description;
    private String category;
    private boolean builtin;
    private String configSchema;
    private String className;
    private String rawJson;
    private Date updateTime;

    public static FlowResolverResponse from(FlowResolver resolver) {
        FlowResolverResponse resp = new FlowResolverResponse();
        resp.setId(resolver.getId());
        resp.setType(resolver.getType());
        resp.setName(resolver.getName());
        resp.setDescription(resolver.getDescription());
        resp.setCategory(resolver.getCategory());
        resp.setBuiltin(resolver.getBuiltin() != null && resolver.getBuiltin() == 1);
        resp.setConfigSchema(resolver.getConfigSchema());
        resp.setClassName(resolver.getClassName());
        resp.setRawJson(resolver.getRawJson());
        resp.setUpdateTime(resolver.getUpdateTime());
        return resp;
    }
}






package org.yglue.flow.orch.web.dto.paramassembler.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ParamAssemblerArgMeta {
    @NotBlank
    private String name;

    private String javaType;

    private Boolean required;

    private Map<String, Object> schema;
}

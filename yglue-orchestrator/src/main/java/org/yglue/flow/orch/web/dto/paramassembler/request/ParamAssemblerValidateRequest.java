package org.yglue.flow.orch.web.dto.paramassembler.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ParamAssemblerValidateRequest {
    private Map<String, Object> ast = new LinkedHashMap<>();

    @Valid
    private List<ParamAssemblerArgMeta> args = new ArrayList<>();
}

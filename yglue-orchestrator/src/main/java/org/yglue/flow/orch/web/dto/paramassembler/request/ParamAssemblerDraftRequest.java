package org.yglue.flow.orch.web.dto.paramassembler.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParamAssemblerDraftRequest {
    @Valid
    private List<ParamAssemblerArgMeta> args = new ArrayList<>();

    private List<String> sourcePaths = new ArrayList<>();
}

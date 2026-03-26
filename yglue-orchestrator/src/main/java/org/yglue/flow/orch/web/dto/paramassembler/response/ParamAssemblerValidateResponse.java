package org.yglue.flow.orch.web.dto.paramassembler.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParamAssemblerValidateResponse {
    private boolean valid;

    private List<ParamAssemblerIssueResponse> issues = new ArrayList<>();
}

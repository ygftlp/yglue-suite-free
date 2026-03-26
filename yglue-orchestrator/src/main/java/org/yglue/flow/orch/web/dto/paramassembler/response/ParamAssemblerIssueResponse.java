package org.yglue.flow.orch.web.dto.paramassembler.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParamAssemblerIssueResponse {
    private String code;
    private String path;
    private String severity;
    private String message;
}

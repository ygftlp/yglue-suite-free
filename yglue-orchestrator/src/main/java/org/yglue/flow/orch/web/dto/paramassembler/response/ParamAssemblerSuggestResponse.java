package org.yglue.flow.orch.web.dto.paramassembler.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ParamAssemblerSuggestResponse {
    private Map<String, Object> ast = new LinkedHashMap<>();

    private List<ParamAssemblerIssueResponse> issues = new ArrayList<>();

    private int updatedCount;

    private List<String> touchedArgs = new ArrayList<>();

    private String summary;
}

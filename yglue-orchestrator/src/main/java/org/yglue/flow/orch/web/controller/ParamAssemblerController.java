package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.service.ParamAssemblerService;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerDraftRequest;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerValidateRequest;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerContextResponse;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerDraftResponse;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerValidateResponse;

@RestController
@RequestMapping("/api/projects/{projectKey}/param-assembler")
public class ParamAssemblerController {

    private final ParamAssemblerService paramAssemblerService;

    public ParamAssemblerController(ParamAssemblerService paramAssemblerService) {
        this.paramAssemblerService = paramAssemblerService;
    }

    @GetMapping("/context")
    public ParamAssemblerContextResponse context(@PathVariable("projectKey") String projectKey,
                                                 @RequestParam(name = "endpointId", required = false) Long endpointId) {
        return paramAssemblerService.buildContext(projectKey, endpointId);
    }

    @PostMapping("/draft")
    public ParamAssemblerDraftResponse draft(@PathVariable("projectKey") String projectKey,
                                             @RequestBody @Valid ParamAssemblerDraftRequest request) {
        return paramAssemblerService.buildDraft(projectKey, request);
    }

    @PostMapping("/validate")
    public ParamAssemblerValidateResponse validate(@PathVariable("projectKey") String projectKey,
                                                   @RequestBody @Valid ParamAssemblerValidateRequest request) {
        return paramAssemblerService.validate(projectKey, request);
    }
}

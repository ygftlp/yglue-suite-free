package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.FlowEntryPoint;
import org.yglue.flow.orch.service.FlowEntryPointService;
import org.yglue.flow.orch.web.dto.flow.request.FlowEntryPointToggleRequest;
import org.yglue.flow.orch.web.dto.flow.response.EntryPointsResponse;
import org.yglue.flow.orch.web.dto.flow.response.RestEntryPointResponse;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectKey}/entrypoints")
public class FlowEntryPointController {

    private final FlowEntryPointService flowEntryPointService;

    public FlowEntryPointController(FlowEntryPointService flowEntryPointService) {
        this.flowEntryPointService = flowEntryPointService;
    }

    @GetMapping
    public EntryPointsResponse list(@PathVariable("projectKey") String projectKey) {
        List<FlowEntryPoint> entryPoints = flowEntryPointService.listByProject(projectKey);
        List<RestEntryPointResponse> rests = entryPoints.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new EntryPointsResponse(rests);
    }

    /**
     * 根据流程代码获取入口点信息
     * 
     * @param projectKey 项目标识
     * @param flowCode 流程代码
     * @return 入口点响应对象
     * @throws ResponseStatusException 如果入口点不存在则返回 404
     */
    @GetMapping("/flows/{flowCode}")
    public RestEntryPointResponse getByFlowCode(@PathVariable("projectKey") String projectKey,
                                                 @PathVariable("flowCode") String flowCode) {
        FlowEntryPoint entryPoint = flowEntryPointService.getByProjectAndFlow(projectKey, flowCode);
        if (entryPoint == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flow entry point not found for flow code: " + flowCode);
        }
        return toResponse(entryPoint);
    }

    @PatchMapping("/{entrypointId}")
    public RestEntryPointResponse toggle(@PathVariable("projectKey") String projectKey,
                                         @PathVariable("entrypointId") Long entrypointId,
                                         @Valid @RequestBody FlowEntryPointToggleRequest request) {
        FlowEntryPoint updated = flowEntryPointService.updateEnabled(
                projectKey,
                entrypointId,
                request.getEnabled(),
                request.getUpdatedBy());
        return toResponse(updated);
    }

    private RestEntryPointResponse toResponse(FlowEntryPoint entryPoint) {
        boolean enabled = entryPoint.getEnabled() == null || entryPoint.getEnabled();
        Object dataResponseFormat = parseDataResponseFormat(entryPoint.getDataResponseFormat());
        Object inboundInterceptors = parseInboundInterceptors(entryPoint.getInboundInterceptorsJson());
        return new RestEntryPointResponse(
                entryPoint.getId(),
                entryPoint.getPath(),
                entryPoint.getHttpMethod(),
                entryPoint.getFlowCode(),
                entryPoint.getRequestSchemaJson(),
                dataResponseFormat,
                inboundInterceptors,
                enabled);
    }

    /**
     * 解析数据响应格式
     * 如果为 JSON 字符串则解析为对象，否则返回原值
     */
    private Object parseDataResponseFormat(String dataResponseFormat) {
        if (dataResponseFormat == null || dataResponseFormat.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    dataResponseFormat, Object.class);
        } catch (Exception e) {
            // 如果不是 JSON，返回原字符串（可能是预设名称如 "standard"）
            return dataResponseFormat;
        }
    }

    private Object parseInboundInterceptors(String inboundInterceptorsJson) {
        if (inboundInterceptorsJson == null || inboundInterceptorsJson.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    inboundInterceptorsJson, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}

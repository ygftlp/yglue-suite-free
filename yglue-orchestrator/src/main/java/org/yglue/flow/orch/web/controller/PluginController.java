package org.yglue.flow.orch.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.service.PluginSyncService;
import org.yglue.flow.orch.web.dto.plugin.request.FlowSyncAckRequest;
import org.yglue.flow.orch.web.dto.plugin.request.PluginHeartbeatRequest;
import org.yglue.flow.orch.web.dto.plugin.response.PluginHeartbeatResponse;
import org.yglue.flow.orch.web.dto.plugin.response.PluginInstanceResponse;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectKey}/plugins")
public class PluginController {

    private final PluginSyncService pluginSyncService;

    public PluginController(PluginSyncService pluginSyncService) {
        this.pluginSyncService = pluginSyncService;
    }

    @PostMapping("/heartbeat")
    public PluginHeartbeatResponse heartbeat(@PathVariable("projectKey") String projectKey,
                                             @RequestBody @Valid PluginHeartbeatRequest request,
                                             HttpServletRequest httpRequest) {
        return pluginSyncService.handleHeartbeat(projectKey, request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/{instanceKey}/sync/ack")
    public void ack(@PathVariable("projectKey") String projectKey,
                    @PathVariable("instanceKey") String instanceKey,
                    @RequestBody @Valid FlowSyncAckRequest request) {
        pluginSyncService.acknowledgeSync(projectKey, instanceKey, request);
    }

    @GetMapping
    public List<PluginInstanceResponse> list(@PathVariable("projectKey") String projectKey) {
        return pluginSyncService.listInstances(projectKey);
    }
}

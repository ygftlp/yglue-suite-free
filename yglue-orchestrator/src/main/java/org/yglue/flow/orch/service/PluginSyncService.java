package org.yglue.flow.orch.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.Flow;
import org.yglue.flow.orch.domain.FlowSyncState;
import org.yglue.flow.orch.domain.FlowVersion;
import org.yglue.flow.orch.domain.PluginInstance;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.persistence.mapper.FlowMapper;
import org.yglue.flow.orch.persistence.mapper.FlowSyncStateMapper;
import org.yglue.flow.orch.persistence.mapper.FlowVersionMapper;
import org.yglue.flow.orch.persistence.mapper.PluginInstanceMapper;
import org.yglue.flow.orch.web.dto.plugin.request.FlowSyncAckRequest;
import org.yglue.flow.orch.web.dto.plugin.request.PluginHeartbeatRequest;
import org.yglue.flow.orch.web.dto.plugin.response.FlowSyncInstructionResponse;
import org.yglue.flow.orch.web.dto.plugin.response.PluginHeartbeatResponse;
import org.yglue.flow.orch.web.dto.plugin.response.PluginInstanceResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PluginSyncService {

    private final ProjectService projectService;
    private final FlowMapper flowMapper;
    private final FlowVersionMapper flowVersionMapper;
    private final PluginInstanceMapper pluginInstanceMapper;
    private final FlowSyncStateMapper flowSyncStateMapper;

    public PluginSyncService(ProjectService projectService,
                             FlowMapper flowMapper,
                             FlowVersionMapper flowVersionMapper,
                             PluginInstanceMapper pluginInstanceMapper,
                             FlowSyncStateMapper flowSyncStateMapper) {
        this.projectService = projectService;
        this.flowMapper = flowMapper;
        this.flowVersionMapper = flowVersionMapper;
        this.pluginInstanceMapper = pluginInstanceMapper;
        this.flowSyncStateMapper = flowSyncStateMapper;
    }

    @Transactional
    public PluginHeartbeatResponse handleHeartbeat(String projectKey,
                                                   PluginHeartbeatRequest req,
                                                   String requesterIp) {
        Project project = projectService.requireProject(projectKey);
        String instanceKey = req.instanceKey.trim();
        Instant now = Instant.now();

        PluginInstance instance = pluginInstanceMapper
                .selectByProjectAndInstance(project.getId(), instanceKey);
        if (instance == null) {
            instance = new PluginInstance();
            instance.setProjectId(project.getId());
            instance.setInstanceKey(instanceKey);
            instance.setIdeType(req.ideType);
            instance.setIdeVersion(req.ideVersion);
            instance.setStatus(defaultStatus(req.status));
            instance.setLastHeartbeat(now);
            instance.setLastIp(resolveIp(req, requesterIp));
            instance.setExtraInfo(req.extraInfo);
            instance.setCreateBy(instanceKey);
            instance.setUpdateBy(instanceKey);
            pluginInstanceMapper.insert(instance);
        } else {
            instance.setIdeType(req.ideType);
            instance.setIdeVersion(req.ideVersion);
            instance.setStatus(defaultStatus(req.status));
            instance.setLastHeartbeat(now);
            instance.setLastIp(resolveIp(req, requesterIp));
            instance.setExtraInfo(req.extraInfo);
            instance.setUpdateBy(instanceKey);
            pluginInstanceMapper.update(instance);
        }

        List<FlowSyncInstructionResponse> pending = computePendingSync(project.getId(), instanceKey);
        return new PluginHeartbeatResponse(!pending.isEmpty(), pending);
    }

    @Transactional
    public void acknowledgeSync(String projectKey,
                                String instanceKey,
                                FlowSyncAckRequest req) {
        Project project = projectService.requireProject(projectKey);
        PluginInstance instance = pluginInstanceMapper.selectByProjectAndInstance(project.getId(), instanceKey);
        if (instance == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Plugin instance not registered: " + instanceKey);
        }

        Flow flow = flowMapper.selectByProjectAndCode(project.getId(), req.flowCode);
        if (flow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flow not found: " + req.flowCode);
        }

        FlowVersion version = flowVersionMapper.selectByFlowIdAndVersion(flow.getId(), req.versionNo);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flow version not found: " + req.versionNo);
        }

        FlowSyncState state = flowSyncStateMapper.selectByFlowAndInstance(flow.getId(), instanceKey);
        Instant now = Instant.now();
        if (state == null) {
            state = new FlowSyncState();
            state.setProjectId(project.getId());
            state.setFlowId(flow.getId());
            state.setInstanceKey(instanceKey);
            state.setLastVersionId(version.getId());
            state.setLastSyncedAt(now);
            state.setCreateBy(instanceKey);
            state.setUpdateBy(instanceKey);
            flowSyncStateMapper.insert(state);
        } else {
            state.setLastVersionId(version.getId());
            state.setLastSyncedAt(now);
            state.setUpdateBy(instanceKey);
            flowSyncStateMapper.update(state);
        }

        instance.setLastHeartbeat(now);
        instance.setStatus(defaultStatus("ONLINE"));
        instance.setUpdateBy(instanceKey);
        pluginInstanceMapper.update(instance);
    }

    public List<PluginInstanceResponse> listInstances(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        List<PluginInstance> instances = pluginInstanceMapper.selectByProject(project.getId());
        List<Flow> flows = flowMapper.selectByProject(project.getId());
        Map<Long, FlowVersion> latestVersions = loadLatestVersions(flows);

        List<PluginInstanceResponse> responses = new ArrayList<>();
        for (PluginInstance instance : instances) {
            int pendingCount = computePendingCount(flows, latestVersions, instance.getInstanceKey(), project.getId());
            PluginInstanceResponse resp = new PluginInstanceResponse();
            resp.setInstanceKey(instance.getInstanceKey());
            resp.setIdeType(instance.getIdeType());
            resp.setIdeVersion(instance.getIdeVersion());
            resp.setStatus(instance.getStatus());
            resp.setLastHeartbeat(instance.getLastHeartbeat());
            resp.setLastIp(instance.getLastIp());
            resp.setPendingFlows(pendingCount);
            responses.add(resp);
        }
        return responses;
    }

    private List<FlowSyncInstructionResponse> computePendingSync(Long projectId, String instanceKey) {
        List<Flow> flows = flowMapper.selectByProject(projectId);
        Map<Long, FlowVersion> latestVersions = loadLatestVersions(flows);
        Map<Long, FlowSyncState> stateMap = flowSyncStateMapper
                .selectByProjectAndInstance(projectId, instanceKey)
                .stream()
                .collect(Collectors.toMap(FlowSyncState::getFlowId, Function.identity()));

        List<FlowSyncInstructionResponse> pending = new ArrayList<>();
        for (Flow flow : flows) {
            FlowVersion latest = latestVersions.get(flow.getLatestVersionId());
            if (latest == null) {
                continue;
            }
            FlowSyncState state = stateMap.get(flow.getId());
            if (state == null || !Objects.equals(state.getLastVersionId(), latest.getId())) {
                FlowSyncInstructionResponse item = new FlowSyncInstructionResponse();
                item.setFlowCode(flow.getCode());
                item.setFlowName(flow.getName());
                item.setLatestVersion(latest.getVersionNo());
                item.setUpdatedAt(latest.getUpdateTime());
                pending.add(item);
            }
        }
        return pending;
    }

    private int computePendingCount(List<Flow> flows,
                                    Map<Long, FlowVersion> latestVersions,
                                    String instanceKey,
                                    Long projectId) {
        Map<Long, FlowSyncState> stateMap = flowSyncStateMapper
                .selectByProjectAndInstance(projectId, instanceKey)
                .stream()
                .collect(Collectors.toMap(FlowSyncState::getFlowId, Function.identity()));

        int count = 0;
        for (Flow flow : flows) {
            FlowVersion latest = latestVersions.get(flow.getLatestVersionId());
            if (latest == null) {
                continue;
            }
            FlowSyncState state = stateMap.get(flow.getId());
            if (state == null || !Objects.equals(state.getLastVersionId(), latest.getId())) {
                count++;
            }
        }
        return count;
    }

    private Map<Long, FlowVersion> loadLatestVersions(List<Flow> flows) {
        List<Long> versionIds = flows.stream()
                .map(Flow::getLatestVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return flowVersionMapper.selectByIds(versionIds)
                .stream()
                .collect(Collectors.toMap(FlowVersion::getId, Function.identity()));
    }

    private String resolveIp(PluginHeartbeatRequest req, String requesterIp) {
        return req.hostIp != null && !req.hostIp.isBlank() ? req.hostIp : requesterIp;
    }

    private String defaultStatus(String status) {
        return (status == null || status.isBlank()) ? "ONLINE" : status;
    }
}

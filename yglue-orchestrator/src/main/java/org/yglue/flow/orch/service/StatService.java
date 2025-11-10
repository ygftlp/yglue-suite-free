package org.yglue.flow.orch.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yglue.flow.orch.domain.Flow;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.StatEvent;
import org.yglue.flow.orch.persistence.mapper.FlowMapper;
import org.yglue.flow.orch.persistence.mapper.StatEventMapper;
import org.yglue.flow.orch.web.dto.stat.request.StatEventRequest;

import java.time.Instant;
import java.util.List;

@Service
public class StatService {

    private final ProjectService projectService;
    private final FlowMapper flowMapper;
    private final StatEventMapper statEventMapper;

    public StatService(ProjectService projectService,
                       FlowMapper flowMapper,
                       StatEventMapper statEventMapper) {
        this.projectService = projectService;
        this.flowMapper = flowMapper;
        this.statEventMapper = statEventMapper;
    }

    @Transactional
    public StatEvent recordEvent(String projectKey, StatEventRequest req) {
        Project project = projectService.requireProject(projectKey);
        StatEvent event = new StatEvent();
        event.setProjectId(project.getId());
        if (req.flowCode != null) {
            Flow flow = flowMapper.selectByProjectAndCode(project.getId(), req.flowCode);
            if (flow != null) {
                event.setFlowId(flow.getId());
            }
        }
        event.setEventType(req.eventType);
        event.setSource(req.source);
        event.setPayloadJson(req.payloadJson);
        event.setEventTs(Instant.now());
        event.setCreateBy(req.source);
        event.setUpdateBy(req.source);
        statEventMapper.insert(event);
        return event;
    }

    public List<StatEvent> list(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        return statEventMapper.selectByProject(project.getId());
    }
}

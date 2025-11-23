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

/**
 * 统计事件服务类
 * <p>
 * 提供统计事件的记录和查询功能。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
@Service
public class StatService {

    private final ProjectService projectService;
    private final FlowMapper flowMapper;
    private final StatEventMapper statEventMapper;

    /**
     * 构造函数
     *
     * @param projectService 项目服务
     * @param flowMapper 流程数据访问对象
     * @param statEventMapper 统计事件数据访问对象
     */
    public StatService(ProjectService projectService,
                       FlowMapper flowMapper,
                       StatEventMapper statEventMapper) {
        this.projectService = projectService;
        this.flowMapper = flowMapper;
        this.statEventMapper = statEventMapper;
    }

    /**
     * 记录统计事件
     * <p>
     * 如果请求中包含流程代码，会自动关联到对应的流程。
     * </p>
     *
     * @param projectKey 项目标识
     * @param req 统计事件请求对象
     * @return 创建的统计事件对象
     */
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

    /**
     * 查询项目的所有统计事件
     *
     * @param projectKey 项目标识
     * @return 统计事件列表
     */
    public List<StatEvent> list(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        return statEventMapper.selectByProject(project.getId());
    }
}

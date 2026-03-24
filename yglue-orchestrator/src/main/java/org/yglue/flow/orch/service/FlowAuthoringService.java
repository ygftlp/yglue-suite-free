package org.yglue.flow.orch.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.Flow;
import org.yglue.flow.orch.domain.FlowVersion;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.persistence.mapper.FlowMapper;
import org.yglue.flow.orch.persistence.mapper.FlowVersionMapper;
import org.yglue.flow.orch.web.dto.flow.request.FlowPublishRequest;
import org.yglue.flow.orch.web.dto.flow.request.FlowSaveRequest;

@Service
public class FlowAuthoringService {

    private final FlowService flowService;
    private final ParamAssemblerService paramAssemblerService;
    private final ProjectService projectService;
    private final FlowMapper flowMapper;
    private final FlowVersionMapper flowVersionMapper;

    public FlowAuthoringService(FlowService flowService,
                                ParamAssemblerService paramAssemblerService,
                                ProjectService projectService,
                                FlowMapper flowMapper,
                                FlowVersionMapper flowVersionMapper) {
        this.flowService = flowService;
        this.paramAssemblerService = paramAssemblerService;
        this.projectService = projectService;
        this.flowMapper = flowMapper;
        this.flowVersionMapper = flowVersionMapper;
    }

    @Transactional
    public FlowVersion saveFlow(String projectKey, FlowSaveRequest request) {
        request.contentJson = paramAssemblerService.prepareFlowContentForSave(projectKey, request.contentJson);
        return flowService.saveFlow(projectKey, request);
    }

    @Transactional
    public FlowVersion publishFlow(String projectKey, String code, FlowPublishRequest request) {
        Project project = projectService.requireProject(projectKey);
        Flow flow = flowMapper.selectByProjectAndCode(project.getId(), code);
        if (flow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow not found: " + code);
        }

        FlowVersion version = flowVersionMapper.selectByFlowIdAndVersion(flow.getId(), request.versionNo);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flow version not found: " + code + "@" + request.versionNo);
        }

        String preparedContent = paramAssemblerService.prepareFlowContentForPublish(projectKey, version.getContentJson());
        if (!java.util.Objects.equals(version.getContentJson(), preparedContent)) {
            flowVersionMapper.updateContentJson(version.getId(), preparedContent, request.publishedBy);
            version.setContentJson(preparedContent);
        }

        return flowService.publishFlow(projectKey, code, request);
    }
}

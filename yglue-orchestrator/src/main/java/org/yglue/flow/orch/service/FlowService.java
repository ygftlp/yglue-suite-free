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
import org.yglue.flow.orch.web.dto.flow.request.FlowSaveRequest;
import org.yglue.flow.orch.web.dto.flow.request.FlowPublishRequest;

import java.util.List;

@Service
public class FlowService {

    private final ProjectService projectService;
    private final FlowMapper flowMapper;
    private final FlowVersionMapper flowVersionMapper;
    private final FlowEntryPointService flowEntryPointService;

    public FlowService(ProjectService projectService,
                       FlowMapper flowMapper,
                       FlowVersionMapper flowVersionMapper,
                       FlowEntryPointService flowEntryPointService) {
        this.projectService = projectService;
        this.flowMapper = flowMapper;
        this.flowVersionMapper = flowVersionMapper;
        this.flowEntryPointService = flowEntryPointService;
    }

    @Transactional
    public FlowVersion saveFlow(String projectKey, FlowSaveRequest req) {
        Project project = projectService.requireProject(projectKey);
        Flow flow = flowMapper.selectByProjectAndCode(project.getId(), req.code);
        if (flow == null) {
            flow = new Flow();
            flow.setProjectId(project.getId());
            flow.setCode(req.code);
            flow.setName(req.name);
            flow.setCreateBy(req.createdBy);
            flow.setUpdateBy(req.createdBy);
            flowMapper.insert(flow);
        } else if (!req.name.equals(flow.getName())) {
            flow.setName(req.name);
            flow.setUpdateBy(req.createdBy);
            flowMapper.update(flow);
        }

        Integer maxVersion = flowVersionMapper.selectMaxVersion(flow.getId());
        int nextVersion = (maxVersion == null || maxVersion < 1) ? 1 : maxVersion + 1;

        FlowVersion version = new FlowVersion();
        version.setFlowId(flow.getId());
        version.setVersionNo(nextVersion);
        version.setContentJson(req.contentJson);
        version.setCreateBy(req.createdBy);
        version.setUpdateBy(req.createdBy);
        flowVersionMapper.insert(version);

        flowMapper.updateLatestVersion(flow.getId(), version.getId());
        flow.setLatestVersionId(version.getId());
        version.setPublished(Boolean.FALSE);

        return version;
    }

    public List<Flow> listFlows(String projectKey) {
        Project project = projectService.requireProject(projectKey);
        return flowMapper.selectByProject(project.getId());
    }

    public List<FlowVersion> listVersions(String projectKey, String code) {
        Flow flow = requireFlow(projectKey, code);
        Long publishedId = flow.getPublishedVersionId();
        List<FlowVersion> versions = flowVersionMapper.selectByFlowIdDesc(flow.getId());
        for (FlowVersion version : versions) {
            boolean isPublished = publishedId != null && publishedId.equals(version.getId());
            version.setPublished(isPublished);
        }
        return versions;
    }

    public FlowVersion getVersion(String projectKey, String code, Integer versionNo) {
        Flow flow = requireFlow(projectKey, code);
        FlowVersion version = flowVersionMapper.selectByFlowIdAndVersion(flow.getId(), versionNo);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flow version not found: " + code + "@" + versionNo);
        }
        boolean isPublished = flow.getPublishedVersionId() != null
                && flow.getPublishedVersionId().equals(version.getId());
        version.setPublished(isPublished);
        return version;
    }

    @Transactional
    public FlowVersion publishFlow(String projectKey, String code, FlowPublishRequest req) {
        Project project = projectService.requireProject(projectKey);
        Flow flow = flowMapper.selectByProjectAndCode(project.getId(), code);
        if (flow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow not found: " + code);
        }
        FlowVersion version = flowVersionMapper.selectByFlowIdAndVersion(flow.getId(), req.versionNo);
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flow version not found: " + code + "@" + req.versionNo);
        }
        flowMapper.updatePublishedVersion(flow.getId(), version.getId(), req.publishedBy);
        flow.setPublishedVersionId(version.getId());
        version.setPublished(Boolean.TRUE);
        flowEntryPointService.replaceEntryPoints(project, flow.getCode(), req.entrypoint, req.publishedBy);
        return version;
    }

    private Flow requireFlow(String projectKey, String code) {
        Project project = projectService.requireProject(projectKey);
        Flow flow = flowMapper.selectByProjectAndCode(project.getId(), code);
        if (flow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow not found: " + code);
        }
        return flow;
    }
}

package org.yglue.flow.orch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yglue.flow.orch.domain.Flow;
import org.yglue.flow.orch.domain.FlowModel;
import org.yglue.flow.orch.domain.FlowResolver;
import org.yglue.flow.orch.domain.PluginInstance;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.ProjectEndpoint;
import org.yglue.flow.orch.domain.ProjectMetadata;
import org.yglue.flow.orch.domain.code.ProjectClassAggregate;
import org.yglue.flow.orch.domain.code.ProjectJarDependency;
import org.yglue.flow.orch.persistence.mapper.FlowMapper;
import org.yglue.flow.orch.persistence.mapper.FlowModelMapper;
import org.yglue.flow.orch.persistence.mapper.FlowResolverMapper;
import org.yglue.flow.orch.persistence.mapper.PluginInstanceMapper;
import org.yglue.flow.orch.persistence.mapper.ProjectCodeMapper;
import org.yglue.flow.orch.persistence.mapper.ProjectEndpointMapper;
import org.yglue.flow.orch.persistence.mapper.ProjectMetadataMapper;
import org.yglue.flow.orch.web.dto.project.response.ProjectReadinessResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectReadinessServiceTest {

    private ProjectService projectService;
    private ProjectMetadataMapper projectMetadataMapper;
    private ProjectEndpointMapper projectEndpointMapper;
    private FlowMapper flowMapper;
    private PluginInstanceMapper pluginInstanceMapper;
    private ProjectCodeMapper projectCodeMapper;
    private FlowModelMapper flowModelMapper;
    private FlowResolverMapper flowResolverMapper;
    private ProjectReadinessService service;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        projectMetadataMapper = mock(ProjectMetadataMapper.class);
        projectEndpointMapper = mock(ProjectEndpointMapper.class);
        flowMapper = mock(FlowMapper.class);
        pluginInstanceMapper = mock(PluginInstanceMapper.class);
        projectCodeMapper = mock(ProjectCodeMapper.class);
        flowModelMapper = mock(FlowModelMapper.class);
        flowResolverMapper = mock(FlowResolverMapper.class);
        service = new ProjectReadinessService(
                projectService,
                projectMetadataMapper,
                projectEndpointMapper,
                flowMapper,
                pluginInstanceMapper,
                projectCodeMapper,
                flowModelMapper,
                flowResolverMapper
        );
    }

    @Test
    void returnsMissingProjectStateWithoutTouchingPersistenceMappers() {
        when(projectService.findByKey("demo")).thenReturn(null);

        ProjectReadinessResponse response = service.getReadiness("demo");

        assertFalse(response.isProjectExists());
        assertEquals(1, response.getBlockingItems().size());
        assertEquals("PROJECT_MISSING", response.getBlockingItems().get(0).getCode());
        assertFalse(response.isReadyForOrchestration());
        assertEquals("创建项目", response.getNextActions().get(0).getLabel());
        verifyNoInteractions(
                projectMetadataMapper,
                projectEndpointMapper,
                flowMapper,
                pluginInstanceMapper,
                projectCodeMapper,
                flowModelMapper,
                flowResolverMapper
        );
    }

    @Test
    void aggregatesReadinessSignalsForConfiguredProject() {
        Project project = new Project();
        project.setId(9L);
        project.setKey("demo");
        project.setName("Demo");
        when(projectService.findByKey("demo")).thenReturn(project);

        when(projectMetadataMapper.selectByProject(9L)).thenReturn(List.of(new ProjectMetadata()));
        when(projectEndpointMapper.selectByProjectId(9L)).thenReturn(List.of(
                endpoint("REST"),
                endpoint("SERVICE")
        ));
        when(flowMapper.selectByProject(9L)).thenReturn(List.of(new Flow()));
        when(pluginInstanceMapper.selectByProject(9L)).thenReturn(List.of(new PluginInstance()));
        when(projectCodeMapper.listValidDependenciesByProject(9L)).thenReturn(List.of(
                dependency(true),
                dependency(false)
        ));
        when(projectCodeMapper.listValidAggregatesByProject(9L))
                .thenReturn(List.of(new ProjectClassAggregate()));
        when(flowModelMapper.selectActiveByProjectId(9L)).thenReturn(List.of(new FlowModel()));
        when(flowResolverMapper.selectActiveByProjectId(9L)).thenReturn(List.of(new FlowResolver()));

        ProjectReadinessResponse response = service.getReadiness("demo");

        assertTrue(response.isProjectExists());
        assertTrue(response.isMetadataPresent());
        assertTrue(response.isComponentDirectoryReady());
        assertTrue(response.isRestEndpointsReady());
        assertTrue(response.isFlowsReady());
        assertTrue(response.isPluginInstancesReady());
        assertTrue(response.isHelperClassesReady());
        assertTrue(response.isSelectedJarsReady());
        assertTrue(response.isReadyForOrchestration());
        assertTrue(response.isReadyForSmartAssembly());
        assertTrue(response.isReadyForPublishing());
        assertTrue(response.isReadyForSync());
        assertEquals(1, response.getComponentEndpointCount());
        assertEquals(1, response.getRestEndpointCount());
        assertEquals(1, response.getSelectedJarCount());
        assertEquals(1, response.getHelperClassCount());
        assertTrue(response.getBlockingItems().isEmpty());
        assertTrue(response.getWarningItems().isEmpty());
        assertEquals("进入入口工作台", response.getNextActions().get(0).getLabel());
    }

    private static ProjectEndpoint endpoint(String endpointType) {
        ProjectEndpoint endpoint = new ProjectEndpoint();
        endpoint.setEndpointType(endpointType);
        return endpoint;
    }

    private static ProjectJarDependency dependency(boolean selected) {
        ProjectJarDependency dependency = new ProjectJarDependency();
        dependency.setSelected(selected);
        return dependency;
    }
}

package org.yglue.flow.orch.service;

import org.springframework.stereotype.Service;
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
import org.yglue.flow.orch.web.dto.project.response.ProjectReadinessResponse.ProjectReadinessAction;
import org.yglue.flow.orch.web.dto.project.response.ProjectReadinessResponse.ProjectReadinessIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ProjectReadinessService {

    private final ProjectService projectService;
    private final ProjectMetadataMapper projectMetadataMapper;
    private final ProjectEndpointMapper projectEndpointMapper;
    private final FlowMapper flowMapper;
    private final PluginInstanceMapper pluginInstanceMapper;
    private final ProjectCodeMapper projectCodeMapper;
    private final FlowModelMapper flowModelMapper;
    private final FlowResolverMapper flowResolverMapper;

    public ProjectReadinessService(ProjectService projectService,
                                   ProjectMetadataMapper projectMetadataMapper,
                                   ProjectEndpointMapper projectEndpointMapper,
                                   FlowMapper flowMapper,
                                   PluginInstanceMapper pluginInstanceMapper,
                                   ProjectCodeMapper projectCodeMapper,
                                   FlowModelMapper flowModelMapper,
                                   FlowResolverMapper flowResolverMapper) {
        this.projectService = projectService;
        this.projectMetadataMapper = projectMetadataMapper;
        this.projectEndpointMapper = projectEndpointMapper;
        this.flowMapper = flowMapper;
        this.pluginInstanceMapper = pluginInstanceMapper;
        this.projectCodeMapper = projectCodeMapper;
        this.flowModelMapper = flowModelMapper;
        this.flowResolverMapper = flowResolverMapper;
    }

    public ProjectReadinessResponse getReadiness(String projectKey) {
        ProjectReadinessResponse response = new ProjectReadinessResponse();
        response.setProjectKey(projectKey);

        Project project = projectService.findByKey(projectKey);
        if (project == null) {
            response.setProjectExists(false);
            response.getBlockingItems().add(issue(
                    "PROJECT_MISSING",
                    "项目不存在",
                    "当前 projectKey 还没有对应项目，前端工作区暂时无法继续进入服务编排。"
            ));
            return finalizeResponse(response);
        }

        response.setProjectExists(true);
        response.setProjectId(project.getId());
        response.setProjectName(project.getName());

        List<ProjectMetadata> metadataList = projectMetadataMapper.selectByProject(project.getId());
        List<ProjectEndpoint> endpoints = projectEndpointMapper.selectByProjectId(project.getId());
        List<Flow> flows = flowMapper.selectByProject(project.getId());
        List<PluginInstance> pluginInstances = pluginInstanceMapper.selectByProject(project.getId());
        List<ProjectJarDependency> dependencies = projectCodeMapper.listValidDependenciesByProject(project.getId());
        List<ProjectClassAggregate> helperClasses = projectCodeMapper.listValidAggregatesByProject(project.getId());
        List<FlowModel> flowModels = flowModelMapper.selectActiveByProjectId(project.getId());
        List<FlowResolver> flowResolvers = flowResolverMapper.selectActiveByProjectId(project.getId());

        int restEndpointCount = 0;
        int componentEndpointCount = 0;
        for (ProjectEndpoint endpoint : endpoints) {
            String endpointType = endpoint.getEndpointType() == null
                    ? ""
                    : endpoint.getEndpointType().trim().toUpperCase(Locale.ROOT);
            if ("REST".equals(endpointType)) {
                restEndpointCount += 1;
            } else {
                componentEndpointCount += 1;
            }
        }

        int selectedJarCount = (int) dependencies.stream()
                .filter(dep -> Boolean.TRUE.equals(dep.getSelected()))
                .count();

        response.setMetadataCount(metadataList.size());
        response.setComponentEndpointCount(componentEndpointCount);
        response.setRestEndpointCount(restEndpointCount);
        response.setFlowCount(flows.size());
        response.setPluginInstanceCount(pluginInstances.size());
        response.setHelperClassCount(helperClasses.size());
        response.setSelectedJarCount(selectedJarCount);
        response.setFlowModelCount(flowModels.size());
        response.setFlowResolverCount(flowResolvers.size());

        response.setMetadataPresent(!metadataList.isEmpty());
        response.setComponentDirectoryReady(componentEndpointCount > 0);
        response.setRestEndpointsReady(restEndpointCount > 0);
        response.setFlowsReady(!flows.isEmpty());
        response.setPluginInstancesReady(!pluginInstances.isEmpty());
        response.setHelperClassesReady(!helperClasses.isEmpty());
        response.setSelectedJarsReady(selectedJarCount > 0);
        response.setFlowModelsReady(!flowModels.isEmpty());
        response.setFlowResolversReady(!flowResolvers.isEmpty());

        if (!response.isMetadataPresent()) {
            response.getBlockingItems().add(issue(
                    "METADATA_MISSING",
                    "缺少项目元数据",
                    "还没有同步 metadata，服务目录、模型和解析器信息都不会完整。"
            ));
        }
        if (!response.isComponentDirectoryReady()) {
            response.getBlockingItems().add(issue(
                    "COMPONENT_DIRECTORY_EMPTY",
                    "缺少可编排组件",
                    "当前项目没有可用的服务组件目录，服务调用节点还无法高效配置。"
            ));
        }
        if (!response.isRestEndpointsReady()) {
            response.getBlockingItems().add(issue(
                    "REST_ENDPOINTS_EMPTY",
                    "缺少 REST 入口",
                    "当前项目还没有 REST 入口，无法从入口列表进入托管编排。"
            ));
        }
        if (!response.isFlowsReady()) {
            response.getBlockingItems().add(issue(
                    "FLOWS_EMPTY",
                    "缺少 Flow 定义",
                    "当前项目还没有保存过 flow，发布和入口绑定能力还不可用。"
            ));
        }

        if (!response.isHelperClassesReady() && !response.isSelectedJarsReady()) {
            response.getWarningItems().add(issue(
                    "CODE_METADATA_THIN",
                    "代码元数据不足",
                    "辅助类和已选依赖都为空，复杂对象、数组和集合的自动补齐会明显退化。"
            ));
        }
        if (!response.isPluginInstancesReady()) {
            response.getWarningItems().add(issue(
                    "PLUGIN_OFFLINE",
                    "插件实例未连接",
                    "当前没有在线插件实例，发布后的规则还无法自动同步到业务工程。"
            ));
        }
        if (!response.isFlowModelsReady()) {
            response.getWarningItems().add(issue(
                    "FLOW_MODELS_EMPTY",
                    "模型目录为空",
                    "缺少 flow model 元数据，部分类型辅助能力会受影响。"
            ));
        }
        if (!response.isFlowResolversReady()) {
            response.getWarningItems().add(issue(
                    "FLOW_RESOLVERS_EMPTY",
                    "解析器目录为空",
                    "缺少项目级 resolver 元数据，部分参数来源说明会不完整。"
            ));
        }

        return finalizeResponse(response);
    }

    private ProjectReadinessResponse finalizeResponse(ProjectReadinessResponse response) {
        boolean readyForOrchestration = response.isProjectExists()
                && response.isMetadataPresent()
                && response.isComponentDirectoryReady();
        boolean readyForSmartAssembly = readyForOrchestration
                && response.isRestEndpointsReady()
                && (response.isHelperClassesReady() || response.isSelectedJarsReady());
        boolean readyForPublishing = response.isProjectExists()
                && response.isRestEndpointsReady()
                && response.isFlowsReady();
        boolean readyForSync = readyForPublishing && response.isPluginInstancesReady();

        response.setReadyForOrchestration(readyForOrchestration);
        response.setReadyForSmartAssembly(readyForSmartAssembly);
        response.setReadyForPublishing(readyForPublishing);
        response.setReadyForSync(readyForSync);
        response.setSummary(buildSummary(response));
        response.setNextActions(buildNextActions(response));
        return response;
    }

    private String buildSummary(ProjectReadinessResponse response) {
        if (!response.isProjectExists()) {
            return "先创建项目，再同步 metadata 和服务目录，编排工作区才能真正可用。";
        }
        if (!response.getBlockingItems().isEmpty()) {
            return "当前仍有关键阻塞项，建议优先补齐元数据、服务目录、REST 入口与首条 Flow。";
        }
        if (!response.isReadyForSmartAssembly()) {
            return "项目已经可以进入编排，但复杂对象和集合参数的智能装配还缺少代码元数据支撑。";
        }
        if (!response.isReadyForSync()) {
            return "当前已经具备设计和发布能力，接下来重点是连接插件实例，把规则同步链路闭环。";
        }
        return "当前项目已经具备编排、智能装配、发布与同步能力，可以直接投入日常使用。";
    }

    private List<ProjectReadinessAction> buildNextActions(ProjectReadinessResponse response) {
        List<ProjectReadinessAction> actions = new ArrayList<>();
        String projectKey = response.getProjectKey();
        String restRoute = buildRestRoute(projectKey);

        if (!response.isProjectExists()) {
            actions.add(action(
                    "CREATE_PROJECT",
                    "创建项目",
                    "先创建一个可复用的 projectKey，后续 metadata、组件目录和 Flow 都会围绕它聚合。",
                    "/",
                    "primary"
            ));
            return actions;
        }

        if (!response.isMetadataPresent()) {
            actions.add(action(
                    "SYNC_METADATA",
                    "同步项目元数据",
                    "先让 IDE 或后端把 metadata 上传到编排中心，服务目录和类型辅助才会完整。",
                    buildGuideRoute(projectKey, "metadata"),
                    "primary"
            ));
        }
        if (!response.isComponentDirectoryReady()) {
            actions.add(action(
                    "SYNC_COMPONENTS",
                    "补齐服务组件目录",
                    "没有服务目录时，服务节点只能手工录入，复杂装配场景的使用成本会很高。",
                    buildGuideRoute(projectKey, "components"),
                    "primary"
            ));
        }
        if (!response.isRestEndpointsReady()) {
            actions.add(action(
                    "CREATE_REST_ENDPOINT",
                    "新增 REST 入口",
                    "先创建一个入口，再绑定 Flow，用户才能从入口视角进入托管编排。",
                    buildGuideRoute(projectKey, "create-rest"),
                    "primary"
            ));
        }
        if (response.isRestEndpointsReady() && !response.isFlowsReady()) {
            actions.add(action(
                    "CREATE_FIRST_FLOW",
                    "设计首条 Flow",
                    "入口建好后，需要至少一个 Flow 才能完成绑定、发布和联调。",
                    buildGuideRoute(projectKey, "design-flow"),
                    "secondary"
            ));
        }
        if (response.isMetadataPresent()
                && response.isComponentDirectoryReady()
                && !response.isHelperClassesReady()
                && !response.isSelectedJarsReady()) {
            actions.add(action(
                    "UPLOAD_CODE_METADATA",
                    "补充代码元数据",
                    "对象嵌套、对象数组、多接口补数和集合装配都依赖足够的代码元数据。",
                    buildGuideRoute(projectKey, "code-metadata"),
                    "secondary"
            ));
        }
        if (response.isReadyForPublishing() && !response.isPluginInstancesReady()) {
            actions.add(action(
                    "CONNECT_PLUGIN",
                    "连接插件实例",
                    "项目已经可以发布，但还无法把最新 Flow 自动同步回业务工程。",
                    buildGuideRoute(projectKey, "plugin"),
                    "secondary"
            ));
        }
        if (actions.isEmpty()) {
            actions.add(action(
                    "OPEN_REST_CENTER",
                    "进入入口工作台",
                    "从 REST 入口页继续绑定 Flow、检查托管状态，或直接进入 Studio 深化编排。",
                    restRoute,
                    "primary"
            ));
        }

        return actions;
    }

    private String buildRestRoute(String projectKey) {
        return "/projects/" + projectKey + "/rests";
    }

    private String buildGuideRoute(String projectKey, String guide) {
        return buildRestRoute(projectKey) + "?guide=" + guide;
    }

    private ProjectReadinessAction action(String code, String label, String detail, String route, String level) {
        return new ProjectReadinessAction(code, label, detail, route, level);
    }

    private ProjectReadinessIssue issue(String code, String title, String detail) {
        return new ProjectReadinessIssue(code, title, detail);
    }
}

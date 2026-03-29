package org.yglue.flow.orch.web.dto.project.response;

import java.util.ArrayList;
import java.util.List;

public class ProjectReadinessResponse {

    private String projectKey;
    private Long projectId;
    private String projectName;
    private boolean projectExists;
    private boolean metadataPresent;
    private boolean componentDirectoryReady;
    private boolean restEndpointsReady;
    private boolean flowsReady;
    private boolean pluginInstancesReady;
    private boolean helperClassesReady;
    private boolean selectedJarsReady;
    private boolean flowModelsReady;
    private boolean flowResolversReady;
    private boolean readyForOrchestration;
    private boolean readyForSmartAssembly;
    private boolean readyForPublishing;
    private boolean readyForSync;
    private String summary;
    private int metadataCount;
    private int componentEndpointCount;
    private int restEndpointCount;
    private int flowCount;
    private int pluginInstanceCount;
    private int helperClassCount;
    private int selectedJarCount;
    private int flowModelCount;
    private int flowResolverCount;
    private List<ProjectReadinessIssue> blockingItems = new ArrayList<>();
    private List<ProjectReadinessIssue> warningItems = new ArrayList<>();
    private List<ProjectReadinessAction> nextActions = new ArrayList<>();

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public boolean isProjectExists() {
        return projectExists;
    }

    public void setProjectExists(boolean projectExists) {
        this.projectExists = projectExists;
    }

    public boolean isMetadataPresent() {
        return metadataPresent;
    }

    public void setMetadataPresent(boolean metadataPresent) {
        this.metadataPresent = metadataPresent;
    }

    public boolean isComponentDirectoryReady() {
        return componentDirectoryReady;
    }

    public void setComponentDirectoryReady(boolean componentDirectoryReady) {
        this.componentDirectoryReady = componentDirectoryReady;
    }

    public boolean isRestEndpointsReady() {
        return restEndpointsReady;
    }

    public void setRestEndpointsReady(boolean restEndpointsReady) {
        this.restEndpointsReady = restEndpointsReady;
    }

    public boolean isFlowsReady() {
        return flowsReady;
    }

    public void setFlowsReady(boolean flowsReady) {
        this.flowsReady = flowsReady;
    }

    public boolean isPluginInstancesReady() {
        return pluginInstancesReady;
    }

    public void setPluginInstancesReady(boolean pluginInstancesReady) {
        this.pluginInstancesReady = pluginInstancesReady;
    }

    public boolean isHelperClassesReady() {
        return helperClassesReady;
    }

    public void setHelperClassesReady(boolean helperClassesReady) {
        this.helperClassesReady = helperClassesReady;
    }

    public boolean isSelectedJarsReady() {
        return selectedJarsReady;
    }

    public void setSelectedJarsReady(boolean selectedJarsReady) {
        this.selectedJarsReady = selectedJarsReady;
    }

    public boolean isFlowModelsReady() {
        return flowModelsReady;
    }

    public void setFlowModelsReady(boolean flowModelsReady) {
        this.flowModelsReady = flowModelsReady;
    }

    public boolean isFlowResolversReady() {
        return flowResolversReady;
    }

    public void setFlowResolversReady(boolean flowResolversReady) {
        this.flowResolversReady = flowResolversReady;
    }

    public boolean isReadyForOrchestration() {
        return readyForOrchestration;
    }

    public void setReadyForOrchestration(boolean readyForOrchestration) {
        this.readyForOrchestration = readyForOrchestration;
    }

    public boolean isReadyForSmartAssembly() {
        return readyForSmartAssembly;
    }

    public void setReadyForSmartAssembly(boolean readyForSmartAssembly) {
        this.readyForSmartAssembly = readyForSmartAssembly;
    }

    public boolean isReadyForPublishing() {
        return readyForPublishing;
    }

    public void setReadyForPublishing(boolean readyForPublishing) {
        this.readyForPublishing = readyForPublishing;
    }

    public boolean isReadyForSync() {
        return readyForSync;
    }

    public void setReadyForSync(boolean readyForSync) {
        this.readyForSync = readyForSync;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getMetadataCount() {
        return metadataCount;
    }

    public void setMetadataCount(int metadataCount) {
        this.metadataCount = metadataCount;
    }

    public int getComponentEndpointCount() {
        return componentEndpointCount;
    }

    public void setComponentEndpointCount(int componentEndpointCount) {
        this.componentEndpointCount = componentEndpointCount;
    }

    public int getRestEndpointCount() {
        return restEndpointCount;
    }

    public void setRestEndpointCount(int restEndpointCount) {
        this.restEndpointCount = restEndpointCount;
    }

    public int getFlowCount() {
        return flowCount;
    }

    public void setFlowCount(int flowCount) {
        this.flowCount = flowCount;
    }

    public int getPluginInstanceCount() {
        return pluginInstanceCount;
    }

    public void setPluginInstanceCount(int pluginInstanceCount) {
        this.pluginInstanceCount = pluginInstanceCount;
    }

    public int getHelperClassCount() {
        return helperClassCount;
    }

    public void setHelperClassCount(int helperClassCount) {
        this.helperClassCount = helperClassCount;
    }

    public int getSelectedJarCount() {
        return selectedJarCount;
    }

    public void setSelectedJarCount(int selectedJarCount) {
        this.selectedJarCount = selectedJarCount;
    }

    public int getFlowModelCount() {
        return flowModelCount;
    }

    public void setFlowModelCount(int flowModelCount) {
        this.flowModelCount = flowModelCount;
    }

    public int getFlowResolverCount() {
        return flowResolverCount;
    }

    public void setFlowResolverCount(int flowResolverCount) {
        this.flowResolverCount = flowResolverCount;
    }

    public List<ProjectReadinessIssue> getBlockingItems() {
        return blockingItems;
    }

    public void setBlockingItems(List<ProjectReadinessIssue> blockingItems) {
        this.blockingItems = blockingItems;
    }

    public List<ProjectReadinessIssue> getWarningItems() {
        return warningItems;
    }

    public void setWarningItems(List<ProjectReadinessIssue> warningItems) {
        this.warningItems = warningItems;
    }

    public List<ProjectReadinessAction> getNextActions() {
        return nextActions;
    }

    public void setNextActions(List<ProjectReadinessAction> nextActions) {
        this.nextActions = nextActions;
    }

    public static class ProjectReadinessIssue {
        private String code;
        private String title;
        private String detail;

        public ProjectReadinessIssue() {
        }

        public ProjectReadinessIssue(String code, String title, String detail) {
            this.code = code;
            this.title = title;
            this.detail = detail;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }

    public static class ProjectReadinessAction {
        private String code;
        private String label;
        private String detail;
        private String route;
        private String level;

        public ProjectReadinessAction() {
        }

        public ProjectReadinessAction(String code, String label, String detail, String route, String level) {
            this.code = code;
            this.label = label;
            this.detail = detail;
            this.route = route;
            this.level = level;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public String getRoute() {
            return route;
        }

        public void setRoute(String route) {
            this.route = route;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }
    }
}

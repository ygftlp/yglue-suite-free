package org.yglue.flow.orch.web.dto.paramassembler.response;

import lombok.Data;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectComponentGroupResponse;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectEndpointResponse;
import org.yglue.flow.orch.web.dto.flow.response.FlowModelResponse;
import org.yglue.flow.orch.web.dto.flow.response.FlowResolverResponse;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParamAssemblerContextResponse {
    private ProjectEndpointResponse entrypoint;

    private List<String> sourcePaths = new ArrayList<>();

    private List<ProjectComponentGroupResponse> componentGroups = new ArrayList<>();

    private List<FlowModelResponse> models = new ArrayList<>();

    private List<FlowResolverResponse> resolvers = new ArrayList<>();

    private List<SelectedJarItem> selectedJars = new ArrayList<>();

    private List<ClassRefItem> helperClasses = new ArrayList<>();

    @Data
    public static class SelectedJarItem {
        private String name;
        private String coordinate;
        private String jarKey;
        private String groupId;
        private String artifactId;
        private String version;
    }

    @Data
    public static class ClassRefItem {
        private String qualifiedName;
        private String simpleName;
        private String packageName;
        private String kind;
    }
}

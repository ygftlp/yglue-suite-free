package org.yglue.flow.orch.web.dto.paramassembler.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParamAssemblerAnalysisResponse {

    private Summary summary = new Summary();

    private List<TempItem> tempItems = new ArrayList<>();

    private List<ArgItem> argItems = new ArrayList<>();

    private List<String> riskItems = new ArrayList<>();

    private List<ParamAssemblerIssueResponse> issues = new ArrayList<>();

    @Data
    public static class Summary {
        private int argCount;
        private int tempCount;
        private int serviceCallCount;
        private int tempReferenceCount;
        private int objectNodeCount;
        private int listNodeCount;
        private int errorCount;
        private int warningCount;
        private int complexityScore;
        private String complexityLevel = "low";
    }

    @Data
    public static class TempItem {
        private int index;
        private String key;
        private String javaType;
        private List<String> sourceRefs = new ArrayList<>();
        private List<String> tempRefs = new ArrayList<>();
        private List<String> dependsOn = new ArrayList<>();
        private List<String> usedBy = new ArrayList<>();
        private List<ServiceCallItem> serviceCalls = new ArrayList<>();
        private int issueCount;
        private List<String> issueSamples = new ArrayList<>();
    }

    @Data
    public static class ArgItem {
        private String name;
        private String javaType;
        private String summary;
        private List<String> sourceRefs = new ArrayList<>();
        private List<String> tempRefs = new ArrayList<>();
        private List<ServiceCallItem> serviceCalls = new ArrayList<>();
        private int issueCount;
        private List<String> issueSamples = new ArrayList<>();
    }

    @Data
    public static class ServiceCallItem {
        private String label;
        private String fn;
        private int argCount;
        private String resultPath;
    }
}

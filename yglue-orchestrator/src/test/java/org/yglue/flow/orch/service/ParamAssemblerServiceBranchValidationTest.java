package org.yglue.flow.orch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.Project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParamAssemblerServiceBranchValidationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ParamAssemblerService service;

    @BeforeEach
    void setUp() {
        ProjectService projectService = mock(ProjectService.class);
        Project project = new Project();
        project.setKey("demo");
        when(projectService.requireProject("demo")).thenReturn(project);
        service = new ParamAssemblerService(projectService, null, null, null, null, null);
    }

    @Test
    void prepareFlowContentForPublishRejectsForwardBranchTempReferenceInsideServiceCallArgs() throws Exception {
        Map<String, Object> branch = branchNode(
                "branch_1",
                "RouteBranch",
                List.of(
                        branchTempVar(
                                "tmp_age",
                                "userAge",
                                "serviceCall",
                                Map.of(
                                        "serviceCall", serviceCall(
                                                "lookupService",
                                                "ageByName",
                                                List.of(bindingSource("tempVar", "userName"))))),
                        branchTempVar(
                                "tmp_name",
                                "userName",
                                "ctx",
                                Map.of("path", "request.username"))));
        String contentJson = OBJECT_MAPPER.writeValueAsString(flowContent(List.of(branch), List.of()));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.prepareFlowContentForPublish("demo", contentJson));

        assertTrue(ex.getReason().contains("RouteBranch (branch_1)"));
        assertTrue(ex.getReason().contains("earlier tempVar: userName"));
    }

    @Test
    void prepareFlowContentForPublishRejectsMissingBranchTempReferenceInCondition() throws Exception {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("op", "and");
        condition.put("rules", List.of(Map.of(
                "op", "eq",
                "left", Map.of("kind", "tempVar", "tempKey", "userAge"),
                "right", Map.of("kind", "const", "constValue", 18))));

        Map<String, Object> branch = branchNode(
                "branch_1",
                "RouteBranch",
                List.of(branchTempVar(
                        "tmp_name",
                        "userName",
                        "ctx",
                        Map.of("path", "request.username"))));
        String contentJson = OBJECT_MAPPER.writeValueAsString(flowContent(
                List.of(branch),
                List.of(branchEdge("branch_1", "matched_node", condition))));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.prepareFlowContentForPublish("demo", contentJson));

        assertTrue(ex.getReason().contains("RouteBranch (branch_1)"));
        assertTrue(ex.getReason().contains("Referenced branch tempVar does not exist: userAge"));
    }

    @Test
    void prepareFlowContentForPublishAcceptsValidBranchTempVarsAndCondition() throws Exception {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("op", "and");
        condition.put("rules", List.of(Map.of(
                "op", "eq",
                "left", Map.of("kind", "tempVar", "tempKey", "ageSnapshot"),
                "right", Map.of("kind", "const", "constValue", 21))));

        Map<String, Object> branch = branchNode(
                "branch_1",
                "RouteBranch",
                List.of(
                        branchTempVar("tmp_name", "userName", "ctx", Map.of("path", "request.username")),
                        branchTempVar(
                                "tmp_age",
                                "userAge",
                                "serviceCall",
                                Map.of(
                                        "serviceCall", serviceCall(
                                                "lookupService",
                                                "ageByName",
                                                List.of(bindingSource("tempVar", "userName"))))),
                        branchTempVar("tmp_snapshot", "ageSnapshot", "tempVar", Map.of("tempKey", "userAge"))));
        String contentJson = OBJECT_MAPPER.writeValueAsString(flowContent(
                List.of(branch),
                List.of(branchEdge("branch_1", "matched_node", condition))));

        assertEquals(contentJson, service.prepareFlowContentForPublish("demo", contentJson));
    }

    private static Map<String, Object> flowContent(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("nodes", nodes);
        content.put("edges", edges);
        return content;
    }

    private static Map<String, Object> branchNode(String id, String label, List<Map<String, Object>> tempVars) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", label);
        data.put("tempVars", tempVars);

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "branch");
        node.put("data", data);
        return node;
    }

    private static Map<String, Object> branchTempVar(String id,
                                                     String key,
                                                     String kind,
                                                     Map<String, Object> extraFields) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("id", id);
        plan.put("key", key);
        plan.put("kind", kind);
        plan.putAll(extraFields);
        return plan;
    }

    private static Map<String, Object> branchEdge(String source, String target, Map<String, Object> conditionV2) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("priority", 1);
        data.put("conditionV2", conditionV2);

        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        edge.put("data", data);
        return edge;
    }

    private static Map<String, Object> serviceCall(String serviceBean,
                                                   String methodName,
                                                   List<Map<String, Object>> bindingSources) {
        Map<String, Object> serviceCall = new LinkedHashMap<>();
        serviceCall.put("serviceRef", Map.of(
                "serviceBean", serviceBean,
                "methodName", methodName,
                "methodSignature", methodName + "(java.lang.String)"));
        serviceCall.put("argBindings", List.of(Map.of(
                "paramName", "name",
                "paramType", "java.lang.String",
                "source", bindingSources.get(0))));
        return serviceCall;
    }

    private static Map<String, Object> bindingSource(String kind, String tempKey) {
        if ("tempVar".equals(kind)) {
            return Map.of("kind", "tempVar", "tempKey", tempKey);
        }
        return Map.of("kind", "ctx", "path", tempKey);
    }
}

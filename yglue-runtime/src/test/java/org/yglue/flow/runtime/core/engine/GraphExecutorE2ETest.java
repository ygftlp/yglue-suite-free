package org.yglue.flow.runtime.core.engine;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.engine.interceptors.ParamResolveInterceptor;
import org.yglue.flow.runtime.core.executors.BranchNodeExecutor;
import org.yglue.flow.runtime.core.executors.LogNodeExecutor;
import org.yglue.flow.runtime.core.executors.ServiceNodeExecutor;
import org.yglue.flow.runtime.core.executors.SetNodeExecutor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphExecutorE2ETest {

    @Test
    void testParamPlansAreResolvedIntoStructuredArgs() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry()
                .register("log", new LogNodeExecutor());
        GraphExecutor executor = new GraphExecutor(registry, List.of(new ParamResolveInterceptor()));

        Map<String, Object> paramPlans = new LinkedHashMap<>();
        Map<String, Object> argPlan1 = new HashMap<>();
        argPlan1.put("target", "user.name");
        argPlan1.put("source", Map.of("kind", "ctx", "path", "request.username"));

        Map<String, Object> argPlan2 = new HashMap<>();
        argPlan2.put("target", "user.age");
        argPlan2.put("source", Map.of("kind", "expr", "value", "10 + 8"));

        paramPlans.put("argPlans", List.of(argPlan1, argPlan2));

        Map<String, Object> config = new HashMap<>();
        config.put("paramPlans", paramPlans);

        NodeDefinition node = new NodeDefinition("node_a", "log", config, List.of());
        FlowDefinition flow = new FlowDefinition("test_rule_01", Map.of(), List.of(node));

        Map<String, Object> request = new HashMap<>();
        request.put("username", "tester_ast");
        FlowExecutionResult result = executor.execute(flow, Map.of("request", request));

        assertNotNull(result);
        assertTrue(result.getContextSnapshot().containsKey("request"));

        Object resolvedArgsObj = node.getConfig().get("_resolvedArgs");
        assertNotNull(resolvedArgsObj);

        @SuppressWarnings("unchecked")
        List<Object> resolvedArgs = (List<Object>) resolvedArgsObj;
        assertEquals(1, resolvedArgs.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> arg0 = (Map<String, Object>) resolvedArgs.get(0);
        assertEquals("tester_ast", arg0.get("name"));
        assertEquals(18, arg0.get("age"));
    }

    @Test
    void testParamPlansFeedServiceInputs() {
        GenericApplicationContext springContext = new GenericApplicationContext();
        springContext.registerBean("testParamService", TestParamService.class, TestParamService::new);
        springContext.refresh();

        try {
            NodeExecutorRegistry registry = new NodeExecutorRegistry()
                    .register("service", new ServiceNodeExecutor(springContext));
            GraphExecutor executor = new GraphExecutor(registry, List.of(new ParamResolveInterceptor()));

            Map<String, Object> paramPlans = new LinkedHashMap<>();
            Map<String, Object> argPlan1 = new HashMap<>();
            argPlan1.put("target", "name");
            argPlan1.put("source", Map.of("kind", "ctx", "path", "request.username"));

            Map<String, Object> argPlan2 = new HashMap<>();
            argPlan2.put("target", "age");
            argPlan2.put("source", Map.of("kind", "expr", "value", "20 + 1"));

            paramPlans.put("argPlans", List.of(argPlan1, argPlan2));

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("paramPlans", paramPlans);
            config.put("inputs", List.of(
                    Map.of("name", "name"),
                    Map.of("name", "age")));
            config.put("comp", Map.of(
                    "bean", "testParamService",
                    "configJson", "{\"method\":\"join\"}"));
            config.put("as", "serviceResult");

            NodeDefinition node = new NodeDefinition("service_node", "service", config, List.of());
            FlowDefinition flow = new FlowDefinition("service_rule", Map.of(), List.of(node));

            FlowExecutionResult result = executor.execute(flow, Map.of("request", Map.of("username", "alice")));

            assertEquals("alice-21", result.getReturnValue());
            assertEquals("alice-21", result.getContextSnapshot().get("serviceResult"));
        } finally {
            springContext.close();
        }
    }

    @Test
    void testBranchExecutesOnlySelectedEdge() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry()
                .register("branch", new BranchNodeExecutor())
                .register("set", new SetNodeExecutor());
        GraphExecutor executor = new GraphExecutor(registry, List.of());

        NodeDefinition branch = new NodeDefinition("branch_1", "branch", Map.of(), List.of());
        NodeDefinition approved = new NodeDefinition("approved_node", "set",
                Map.of("target", "route", "value", "approved"), List.of());
        NodeDefinition rejected = new NodeDefinition("rejected_node", "set",
                Map.of("target", "route", "value", "rejected"), List.of());

        Map<String, Object> approveCondition = Map.of(
                "op", "and",
                "rules", List.of(Map.of(
                        "op", "eq",
                        "left", Map.of("kind", "ctx", "path", "request.status"),
                        "right", Map.of("kind", "const", "constValue", "approved"))));
        Map<String, Object> rejectCondition = Map.of(
                "op", "and",
                "rules", List.of(Map.of(
                        "op", "eq",
                        "left", Map.of("kind", "ctx", "path", "request.status"),
                        "right", Map.of("kind", "const", "constValue", "rejected"))));

        List<Map<String, Object>> edges = List.of(
                Map.of("source", "branch_1", "target", "approved_node", "data",
                        Map.of("priority", 1, "condition", approveCondition)),
                Map.of("source", "branch_1", "target", "rejected_node", "data",
                        Map.of("priority", 2, "condition", rejectCondition)));

        FlowDefinition flow = new FlowDefinition("branch_rule", Map.of(),
                List.of(branch, approved, rejected), edges);

        FlowExecutionResult result = executor.execute(flow, Map.of("request", Map.of("status", "approved")));

        assertEquals("approved", result.getContextSnapshot().get("route"));
        assertEquals("approved", result.getReturnValue());
    }

    public static class TestParamService {
        public String join(String name, Integer age) {
            return name + "-" + age;
        }
    }
}

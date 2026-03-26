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
import org.yglue.flow.runtime.core.executors.RequestScopedServiceNodeExecutor;
import org.yglue.flow.runtime.core.executors.ServiceNodeExecutor;
import org.yglue.flow.runtime.core.executors.SetNodeExecutor;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutionContext;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        assertNull(node.getConfig().get("_resolvedArgs"));
    }

    @Test
    void testParamPlansFeedServiceInputs() {
        GenericApplicationContext springContext = new GenericApplicationContext();
        springContext.registerBean("testParamService", TestParamService.class, TestParamService::new);
        springContext.refresh();

        try {
            NodeExecutorRegistry registry = new NodeExecutorRegistry()
                    .register("service", new RequestScopedServiceNodeExecutor(new ServiceNodeExecutor(springContext)));
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

    @Test
    void testBranchReadsConditionV2FromEdgeData() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry()
                .register("branch", new BranchNodeExecutor())
                .register("set", new SetNodeExecutor());
        GraphExecutor executor = new GraphExecutor(registry, List.of());

        NodeDefinition branch = new NodeDefinition("branch_1", "branch", Map.of(), List.of());
        NodeDefinition matched = new NodeDefinition("matched_node", "set",
                Map.of("target", "route", "value", "matched"), List.of());

        Map<String, Object> conditionV2 = Map.of(
                "op", "and",
                "rules", List.of(Map.of(
                        "op", "eq",
                        "left", Map.of("kind", "ctx", "path", "request.status"),
                        "right", Map.of("kind", "const", "constValue", "ready"))));

        FlowDefinition flow = new FlowDefinition("branch_condition_v2_rule",
                Map.of(),
                List.of(branch, matched),
                List.of(Map.of(
                        "source", "branch_1",
                        "target", "matched_node",
                        "data", Map.of("priority", 1, "conditionV2", conditionV2))));

        FlowExecutionResult result = executor.execute(flow, Map.of("request", Map.of("status", "ready")));

        assertEquals("matched", result.getContextSnapshot().get("route"));
        assertEquals("matched", result.getReturnValue());
    }

    @Test
    void testParamPlansCanResolveServiceCallSource() {
        GenericApplicationContext springContext = new GenericApplicationContext();
        springContext.registerBean("testParamService", TestParamService.class, TestParamService::new);
        springContext.registerBean("lookupService", LookupService.class, LookupService::new);
        springContext.refresh();

        try {
            NodeExecutorRegistry registry = new NodeExecutorRegistry()
                    .register("service", new RequestScopedServiceNodeExecutor(new ServiceNodeExecutor(springContext)));
            GraphExecutor executor = new GraphExecutor(registry, List.of(new ParamResolveInterceptor(springContext)));

            Map<String, Object> namePlan = new LinkedHashMap<>();
            namePlan.put("target", "name");
            namePlan.put("source", Map.of("kind", "ctx", "path", "request.username"));

            Map<String, Object> serviceCall = new LinkedHashMap<>();
            serviceCall.put("serviceRef", Map.of(
                    "serviceBean", "lookupService",
                    "methodName", "ageByName",
                    "methodSignature", "ageByName(java.lang.String)"));
            serviceCall.put("argBindings", List.of(Map.of(
                    "paramName", "name",
                    "paramType", "java.lang.String",
                    "source", Map.of(
                            "kind", "ctx",
                            "path", "request.username",
                            "mode", "direct",
                            "objectFields", List.of()))));

            Map<String, Object> agePlan = new LinkedHashMap<>();
            agePlan.put("target", "age");
            agePlan.put("source", Map.of(
                    "kind", "serviceCall",
                    "serviceCall", serviceCall));

            Map<String, Object> paramPlans = new LinkedHashMap<>();
            paramPlans.put("argPlans", List.of(namePlan, agePlan));

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
            FlowDefinition flow = new FlowDefinition("service_call_source_rule", Map.of(), List.of(node));

            FlowExecutionResult result = executor.execute(flow, Map.of("request", Map.of("username", "alice")));

            assertEquals("alice-21", result.getReturnValue());
            assertEquals("alice-21", result.getContextSnapshot().get("serviceResult"));
        } finally {
            springContext.close();
        }
    }

    @Test
    void testBranchConditionCanResolveServiceCallSource() {
        GenericApplicationContext springContext = new GenericApplicationContext();
        springContext.registerBean("lookupService", LookupService.class, LookupService::new);
        springContext.refresh();

        try {
            NodeExecutorRegistry registry = new NodeExecutorRegistry()
                    .register("branch", new BranchNodeExecutor(springContext))
                    .register("set", new SetNodeExecutor());
            GraphExecutor executor = new GraphExecutor(registry, List.of());

            NodeDefinition branch = new NodeDefinition("branch_1", "branch", Map.of(), List.of());
            NodeDefinition matched = new NodeDefinition("matched_node", "set",
                    Map.of("target", "route", "value", "matched"), List.of());

            Map<String, Object> serviceCall = new LinkedHashMap<>();
            serviceCall.put("serviceRef", Map.of(
                    "serviceBean", "lookupService",
                    "methodName", "decisionByStatus",
                    "methodSignature", "decisionByStatus(java.lang.String)"));
            serviceCall.put("argBindings", List.of(Map.of(
                    "paramName", "status",
                    "paramType", "java.lang.String",
                    "source", Map.of(
                            "kind", "ctx",
                            "path", "request.status",
                            "mode", "direct",
                            "objectFields", List.of()))));

            Map<String, Object> conditionV2 = Map.of(
                    "op", "and",
                    "rules", List.of(Map.of(
                            "op", "eq",
                            "left", Map.of(
                                    "kind", "serviceCall",
                                    "serviceCall", serviceCall,
                                    "serviceResultPath", "route"),
                            "right", Map.of("kind", "const", "constValue", "approved"))));

            FlowDefinition flow = new FlowDefinition("branch_service_call_rule",
                    Map.of(),
                    List.of(branch, matched),
                    List.of(Map.of(
                            "source", "branch_1",
                            "target", "matched_node",
                            "data", Map.of("priority", 1, "conditionV2", conditionV2))));

            FlowExecutionResult result = executor.execute(flow, Map.of("request", Map.of("status", "ready")));

            assertEquals("matched", result.getContextSnapshot().get("route"));
            assertEquals("matched", result.getReturnValue());
        } finally {
            springContext.close();
        }
    }

    @Test
    void testDagJoinNodeExecutesOnlyOnce() {
        AtomicInteger joinExecutionCount = new AtomicInteger();

        FlowExecutor countingExecutor = new FlowExecutor() {
            @Override
            public Object execute(NodeExecutionContext context) {
                String nodeId = context.getNode().getId();
                if ("join_node".equals(nodeId)) {
                    int count = joinExecutionCount.incrementAndGet();
                    context.getContext().set("joinCount", count);
                    return count;
                }
                context.getContext().set(nodeId, "done");
                return nodeId;
            }
        };

        NodeExecutorRegistry registry = new NodeExecutorRegistry()
                .register("marker", countingExecutor);
        GraphExecutor executor = new GraphExecutor(registry, List.of());

        NodeDefinition entry = new NodeDefinition("entry_node", "entry", Map.of(), List.of());
        NodeDefinition left = new NodeDefinition("left_node", "marker", Map.of(), List.of());
        NodeDefinition right = new NodeDefinition("right_node", "marker", Map.of(), List.of());
        NodeDefinition join = new NodeDefinition("join_node", "marker", Map.of(), List.of());

        List<Map<String, Object>> edges = List.of(
                Map.of("source", "entry_node", "target", "left_node"),
                Map.of("source", "entry_node", "target", "right_node"),
                Map.of("source", "left_node", "target", "join_node"),
                Map.of("source", "right_node", "target", "join_node"));

        FlowDefinition flow = new FlowDefinition("join_rule", Map.of(), List.of(entry, left, right, join), edges);

        FlowExecutionResult result = executor.execute(flow, Map.of());

        assertEquals(1, joinExecutionCount.get());
        assertEquals(1, result.getContextSnapshot().get("joinCount"));
    }

    @Test
    void testBranchTempVarExpressionIsEvaluated() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry()
                .register("branch", new BranchNodeExecutor())
                .register("set", new SetNodeExecutor());
        GraphExecutor executor = new GraphExecutor(registry, List.of());

        NodeDefinition branch = new NodeDefinition("branch_1", "branch", Map.of(
                "tempVars", List.of(
                        Map.of("key", "threshold", "kind", "expression", "expression", "#ctx.request.score + 5"))),
                List.of());
        NodeDefinition matched = new NodeDefinition("matched_node", "set",
                Map.of("target", "route", "value", "matched"), List.of());

        Map<String, Object> condition = Map.of(
                "op", "and",
                "rules", List.of(Map.of(
                        "op", "eq",
                        "left", Map.of("kind", "tempVar", "tempKey", "threshold"),
                        "right", Map.of("kind", "const", "constValue", 15))));

        FlowDefinition flow = new FlowDefinition("branch_temp_expr_rule", Map.of(),
                List.of(branch, matched),
                List.of(Map.of("source", "branch_1", "target", "matched_node", "data",
                        Map.of("priority", 1, "condition", condition))));

        FlowExecutionResult result = executor.execute(flow, Map.of("request", Map.of("score", 10)));

        assertEquals("matched", result.getContextSnapshot().get("route"));
    }

    @Test
    void testBranchTempVarsCanResolveServiceCallAndReferencePreviousTempVar() {
        GenericApplicationContext springContext = new GenericApplicationContext();
        springContext.registerBean("lookupService", LookupService.class, LookupService::new);
        springContext.refresh();

        try {
            NodeExecutorRegistry registry = new NodeExecutorRegistry()
                    .register("branch", new BranchNodeExecutor(springContext))
                    .register("set", new SetNodeExecutor());
            GraphExecutor executor = new GraphExecutor(registry, List.of());

            Map<String, Object> ageLookupCall = new LinkedHashMap<>();
            ageLookupCall.put("serviceRef", Map.of(
                    "serviceBean", "lookupService",
                    "methodName", "ageByName",
                    "methodSignature", "ageByName(java.lang.String)"));
            ageLookupCall.put("argBindings", List.of(Map.of(
                    "paramName", "name",
                    "paramType", "java.lang.String",
                    "source", Map.of("kind", "tempVar", "tempKey", "userName"))));

            NodeDefinition branch = new NodeDefinition("branch_1", "branch", Map.of(
                    "tempVars", List.of(
                            Map.of("key", "userName", "kind", "ctx", "path", "request.username"),
                            Map.of("key", "userAge", "kind", "serviceCall", "serviceCall", ageLookupCall),
                            Map.of("key", "ageSnapshot", "kind", "tempVar", "tempKey", "userAge"))),
                    List.of());
            NodeDefinition matched = new NodeDefinition("matched_node", "set",
                    Map.of("target", "route", "value", "matched"), List.of());

            Map<String, Object> condition = Map.of(
                    "op", "and",
                    "rules", List.of(Map.of(
                            "op", "eq",
                            "left", Map.of("kind", "tempVar", "tempKey", "ageSnapshot"),
                            "right", Map.of("kind", "const", "constValue", 21))));

            FlowDefinition flow = new FlowDefinition("branch_temp_service_rule", Map.of(),
                    List.of(branch, matched),
                    List.of(Map.of("source", "branch_1", "target", "matched_node", "data",
                            Map.of("priority", 1, "conditionV2", condition))));

            FlowExecutionResult result = executor.execute(flow, Map.of("request", Map.of("username", "alice")));

            assertEquals("matched", result.getContextSnapshot().get("route"));
        } finally {
            springContext.close();
        }
    }

    @Test
    void testConcurrentExecutionsDoNotLeakResolvedArgsAcrossRequests() throws Exception {
        GenericApplicationContext springContext = new GenericApplicationContext();
        springContext.registerBean("testParamService", TestParamService.class, TestParamService::new);
        springContext.refresh();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            NodeExecutorRegistry registry = new NodeExecutorRegistry()
                    .register("service", new RequestScopedServiceNodeExecutor(new ServiceNodeExecutor(springContext)));
            GraphExecutor executor = new GraphExecutor(registry, List.of(new ParamResolveInterceptor()));

            Map<String, Object> paramPlans = new LinkedHashMap<>();
            paramPlans.put("argPlans", List.of(
                    Map.of("target", "name", "source", Map.of("kind", "ctx", "path", "request.username")),
                    Map.of("target", "age", "source", Map.of("kind", "expr", "value", "#ctx.request.age"))));

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("paramPlans", paramPlans);
            config.put("inputs", List.of(
                    Map.of("name", "name"),
                    Map.of("name", "age")));
            config.put("comp", Map.of(
                    "bean", "testParamService",
                    "configJson", "{\"method\":\"join\"}"));

            NodeDefinition node = new NodeDefinition("service_node", "service", config, List.of());
            FlowDefinition flow = new FlowDefinition("service_rule", Map.of(), List.of(node));

            Future<FlowExecutionResult> first = pool.submit(executeFlow(executor, flow, "alice", 21));
            Future<FlowExecutionResult> second = pool.submit(executeFlow(executor, flow, "bob", 34));

            assertEquals("alice-21", first.get().getReturnValue());
            assertEquals("bob-34", second.get().getReturnValue());
            assertNull(node.getConfig().get("_resolvedArgs"));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            springContext.close();
        }
    }

    @Test
    void testResolvedArgsAllowNullValues() {
        GenericApplicationContext springContext = new GenericApplicationContext();
        springContext.registerBean("testParamService", TestParamService.class, TestParamService::new);
        springContext.refresh();

        try {
            NodeExecutorRegistry registry = new NodeExecutorRegistry()
                    .register("service", new RequestScopedServiceNodeExecutor(new ServiceNodeExecutor(springContext)));
            GraphExecutor executor = new GraphExecutor(registry, List.of(new ParamResolveInterceptor()));

            Map<String, Object> paramPlans = new LinkedHashMap<>();
            paramPlans.put("argPlans", List.of(
                    Map.of("target", "name", "source", Map.of("kind", "ctx", "path", "request.username")),
                    Map.of("target", "age", "source", Map.of("kind", "ctx", "path", "request.optionalAge"))));

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("paramPlans", paramPlans);
            config.put("inputs", List.of(
                    Map.of("name", "name"),
                    Map.of("name", "age")));
            config.put("comp", Map.of(
                    "bean", "testParamService",
                    "configJson", "{\"method\":\"join\"}"));

            NodeDefinition node = new NodeDefinition("service_node", "service", config, List.of());
            FlowDefinition flow = new FlowDefinition("service_rule", Map.of(), List.of(node));

            FlowExecutionResult result = executor.execute(flow, Map.of("request", Map.of("username", "alice")));

            assertEquals("alice-null", result.getReturnValue());
            assertNull(node.getConfig().get("_resolvedArgs"));
        } finally {
            springContext.close();
        }
    }

    private Callable<FlowExecutionResult> executeFlow(GraphExecutor executor,
            FlowDefinition flow,
            String username,
            int age) {
        return () -> executor.execute(flow, Map.of("request", Map.of("username", username, "age", age)));
    }

    public static class TestParamService {
        public String join(String name, Integer age) {
            return name + "-" + age;
        }
    }

    public static class LookupService {
        public Integer ageByName(String name) {
            if ("alice".equals(name)) {
                return 21;
            }
            if ("bob".equals(name)) {
                return 34;
            }
            return 18;
        }

        public Map<String, Object> decisionByStatus(String status) {
            String route = "ready".equals(status) ? "approved" : "rejected";
            return Map.of("route", route);
        }
    }
}

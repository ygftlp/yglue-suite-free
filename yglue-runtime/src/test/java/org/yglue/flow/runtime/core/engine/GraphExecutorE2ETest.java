package org.yglue.flow.runtime.core.engine;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.engine.interceptors.OutputMapInterceptor;
import org.yglue.flow.runtime.core.engine.interceptors.ParamResolveInterceptor;
import org.yglue.flow.runtime.core.executors.LogNodeExecutor;
import org.yglue.flow.runtime.core.executors.ServiceNodeExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端测试：完全剥离 LiteFlow，纯靠 AST 驱动的 GraphExecutor 集成测试。
 */
public class GraphExecutorE2ETest {

    @Test
    public void testComplexParamPlansAndBranch() throws Exception {
        // 1. 初始化极简上下文和注册表
        GenericApplicationContext springContext = new GenericApplicationContext();
        springContext.refresh();

        NodeExecutorRegistry registry = new NodeExecutorRegistry()
                .register("log", new LogNodeExecutor())
                .register("service", new ServiceNodeExecutor(springContext));

        List<NodeInterceptor> interceptors = Arrays.asList(
                new ParamResolveInterceptor(),
                new OutputMapInterceptor());

        GraphExecutor executor = new GraphExecutor(registry, interceptors);

        // 2. 构造虚拟的前端 FlowDefinition AST 描述
        FlowDefinition flow = new FlowDefinition();
        flow.setId("test_rule_01");

        // 节点1: 一个带 ParamPlans 的日志节点（虽然Log节点不需要，但借用参数解析机制验证）
        NodeDefinition node1 = new NodeDefinition();
        node1.setId("node_a");
        node1.setType("log");

        Map<String, Object> node1Config = new HashMap<>();
        // 构造 paramPlans (AST 形式)
        Map<String, Object> paramPlans = new LinkedHashMap<>();

        Map<String, Object> argPlan1 = new HashMap<>();
        argPlan1.put("target", "user.name");
        argPlan1.put("source", Map.of("kind", "ctx", "path", "request.username"));

        Map<String, Object> argPlan2 = new HashMap<>();
        argPlan2.put("target", "user.age");
        argPlan2.put("source", Map.of("kind", "expr", "value", "10 + 8"));

        paramPlans.put("argPlans", Arrays.asList(argPlan1, argPlan2));
        node1Config.put("paramPlans", paramPlans);
        // 如果是真实 Service 节点，还需要 inputs 声明，LogNode 忽略。

        node1.setConfig(node1Config);

        flow.setNodes(new ArrayList<>(Arrays.asList(node1)));

        // 3. 执行
        Map<String, Object> initialInput = new HashMap<>();
        Map<String, Object> req = new HashMap<>();
        req.put("username", "tester_ast");
        initialInput.put("request", req);

        FlowExecutionResult result = executor.execute(flow, initialInput);

        // 4. 断言
        assertNotNull(result);
        Map<String, Object> ctxVars = (Map<String, Object>) result.getContext();
        assertTrue(ctxVars.containsKey("request"));

        // 验证 ParamResolveInterceptor 是否正确组装了 _resolvedArgs
        Map<String, Object> modifiedConfig = node1.getConfig();
        assertTrue(modifiedConfig.containsKey("_resolvedArgs"));
        List<Object> args = (List<Object>) modifiedConfig.get("_resolvedArgs");

        // 应当包含组合好的 Map: { user: { name: "tester_ast", age: 18 } }
        assertNotNull(args);
        assertEquals(1, args.size());

        Map<String, Object> arg0 = (Map<String, Object>) args.get(0);
        assertTrue(arg0.containsKey("user"));
        Map<String, Object> userArg = (Map<String, Object>) arg0.get("user");
        assertEquals("tester_ast", userArg.get("name"));
        assertEquals(18, userArg.get("age"));
    }
}

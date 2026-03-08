package org.yglue.flow.runtime.core.executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.engine.evaluator.BranchConditionEvaluator;
import org.yglue.flow.runtime.core.engine.interceptors.JsonPathUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Branch executor for new GraphExecutor:
 * Evaluates branch conditions using the zero-compile JSON AST
 * BranchConditionEvaluator.
 * Returns the matched targetNodeId string to instruct the GraphExecutor which
 * path to activate.
 */
public class BranchNodeExecutor implements FlowExecutor {

    private static final Logger log = LoggerFactory.getLogger(BranchNodeExecutor.class);

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(NodeExecutionContext context) {
        String nodeId = context.getNode().getId();
        Map<String, Object> config = context.getNode().getConfig();
        Object defaultConditionObj = config != null ? config.get("condition") : null;

        // V2 新版统一从 NodeExecutionContext 取出映射过的 FlowContext
        FlowContext flowContext = new FlowContext(context.getRuleId(), context.getContext());
        Map<String, Object> tempVars = prepareTempVars(config, flowContext);

        FlowDefinition flow = context.getFlow();
        List<Map<String, Object>> edges = flow != null ? flow.getEdges() : null;
        if (edges == null || edges.isEmpty()) {
            log.warn("[BranchNodeExecutor] no edges found, nodeId={}", nodeId);
            return null;
        }

        List<Map<String, Object>> outgoingEdges = new ArrayList<>();
        for (Map<String, Object> edge : edges) {
            String source = asString(edge.get("source"));
            if (nodeId.equals(source)) {
                outgoingEdges.add(edge);
            }
        }

        if (outgoingEdges.isEmpty()) {
            log.warn("[BranchNodeExecutor] no outgoing edges, nodeId={}", nodeId);
            return null;
        }

        outgoingEdges.sort(Comparator.comparingInt(this::edgePriority));

        for (Map<String, Object> edge : outgoingEdges) {
            String targetNodeId = asString(edge.get("target"));
            Map<String, Object> conditionAst = edgeConditionAst(edge);

            // 如果连线上没有独立配置条件 AST，退回节点上的公共条件（或仅单走配置）
            if (conditionAst == null || conditionAst.isEmpty()) {
                if (defaultConditionObj instanceof Map) {
                    conditionAst = (Map<String, Object>) defaultConditionObj;
                }
            }

            // 使用全新实现的无脚本 AST 执行器运算条件
            boolean conditionMet = BranchConditionEvaluator.evaluate(conditionAst, flowContext, tempVars);

            if (!conditionMet) {
                continue;
            }

            log.info("[BranchNodeExecutor] branch selected, nodeId={}, targetNodeId={}, priority={}",
                    nodeId, targetNodeId, edgePriority(edge));

            // 返给 GraphExecutor 用于分发路由
            return targetNodeId;
        }

        log.warn("[BranchNodeExecutor] no branch matched, nodeId={}", nodeId);
        return null; // 没有匹配到分支时，流程阻断
    }

    private NodeDefinition findNodeById(FlowDefinition flow, String nodeId) {
        if (flow == null || flow.getNodes() == null || nodeId == null) {
            return null;
        }
        for (NodeDefinition node : flow.getNodes()) {
            if (nodeId.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    private int edgePriority(Map<String, Object> edge) {
        Map<String, Object> data = edgeData(edge);
        Object raw = data.get("priority");
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt(((String) raw).trim());
            } catch (Exception ignored) {
            }
        }
        return 100;
    }

    private Map<String, Object> edgeConditionAst(Map<String, Object> edge) {
        Map<String, Object> data = edgeData(edge);
        Object rawAst = data.get("condition");
        if (rawAst instanceof Map) {
            return (Map<String, Object>) rawAst;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> edgeData(Map<String, Object> edge) {
        if (edge == null) {
            return Map.of();
        }
        Object raw = edge.get("data");
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> prepareTempVars(Map<String, Object> config, FlowContext context) {
        if (config == null || context == null) {
            return new HashMap<>();
        }
        Object raw = config.get("tempVars");
        if (!(raw instanceof List<?> plans) || plans.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> resolved = new HashMap<>();
        for (Object item : plans) {
            if (!(item instanceof Map<?, ?> planRaw)) {
                continue;
            }
            Map<String, Object> plan = (Map<String, Object>) planRaw;
            String key = asString(plan.get("key"));
            if (key == null || key.isBlank()) {
                continue;
            }
            String kind = asString(plan.get("kind"));
            Object value;
            if ("const".equals(kind)) {
                value = plan.get("constValue");
            } else if ("expression".equals(kind)) {
                // 不再支持表达式的复杂运算，退回 const，以保证零编译
                value = plan.get("constValue");
            } else {
                value = JsonPathUtil.extract(context.getVariables(), asString(plan.get("path")));
            }
            resolved.put(key, value);
        }

        return resolved;
    }

    private String asString(Object value) {
        if (value == null)
            return null;
        return String.valueOf(value);
    }
}

package org.yglue.flow.runtime.core.executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.engine.evaluator.BranchConditionEvaluator;
import org.yglue.flow.runtime.core.engine.interceptors.JsonPathUtil;
import org.yglue.flow.runtime.core.expression.ExpressionEngines;
import org.yglue.flow.runtime.core.expression.ExpressionEvaluationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates branch node conditions and returns the selected target node id.
 */
public class BranchNodeExecutor implements FlowExecutor {

    private static final Logger log = LoggerFactory.getLogger(BranchNodeExecutor.class);
    private final ApplicationContext applicationContext;

    public BranchNodeExecutor() {
        this(null);
    }

    public BranchNodeExecutor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(NodeExecutionContext context) {
        String nodeId = context.getNode().getId();
        Map<String, Object> config = context.getNode().getConfig();
        Object defaultConditionObj = config != null ? config.get("condition") : null;

        FlowContext flowContext = new FlowContext(
                context.getContext() == null ? null : context.getContext().getRuleId(),
                context.getContext());
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

            if ((conditionAst == null || conditionAst.isEmpty()) && defaultConditionObj instanceof Map) {
                conditionAst = (Map<String, Object>) defaultConditionObj;
            }

            boolean conditionMet = BranchConditionEvaluator.evaluate(conditionAst, flowContext, tempVars, applicationContext);
            if (!conditionMet) {
                continue;
            }

            log.info("[BranchNodeExecutor] branch selected, nodeId={}, targetNodeId={}, priority={}",
                    nodeId, targetNodeId, edgePriority(edge));
            return new BranchSelection(targetNodeId);
        }

        log.warn("[BranchNodeExecutor] no branch matched, nodeId={}", nodeId);
        return null;
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
        Object rawConditionV2 = data.get("conditionV2");
        if (rawConditionV2 instanceof Map) {
            return (Map<String, Object>) rawConditionV2;
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
                value = evaluateExpression(plan, context, resolved);
            } else {
                value = JsonPathUtil.extract(context.getVariables(), asString(plan.get("path")));
            }
            resolved.put(key, value);
        }

        return resolved;
    }

    private Object evaluateExpression(Map<String, Object> plan,
            FlowContext context,
            Map<String, Object> resolvedTempVars) {
        String expression = asString(plan.get("expression"));
        if (expression == null || expression.isBlank()) {
            expression = asString(plan.get("constValue"));
        }
        if (expression == null || expression.isBlank()) {
            return null;
        }

        Map<String, Object> tempVarSnapshot = new LinkedHashMap<>(resolvedTempVars);
        ExpressionEvaluationContext evaluationContext = ExpressionEvaluationContext.builder()
                .variables(context.getVariables())
                .variable("ctx", context.getVariables())
                .variable("tempVars", tempVarSnapshot)
                .variable("__tmp", tempVarSnapshot)
                .attribute("flowContext", context)
                .build();
        return ExpressionEngines.getDefault().evaluate(expression, evaluationContext);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}

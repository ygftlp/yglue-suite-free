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
import org.yglue.flow.runtime.core.engine.support.DynamicValueResolver;
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
            return new LinkedHashMap<>();
        }
        Object raw = config.get("tempVars");
        if (!(raw instanceof List<?> plans) || plans.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Integer> orderByKey = new LinkedHashMap<>();
        for (int index = 0; index < plans.size(); index += 1) {
            Object item = plans.get(index);
            if (!(item instanceof Map<?, ?> planRaw)) {
                continue;
            }
            Map<String, Object> plan = (Map<String, Object>) planRaw;
            String key = normalizeKey(asString(plan.get("key")));
            if (key == null || key.isBlank()) {
                continue;
            }
            orderByKey.putIfAbsent(key, index);
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (int index = 0; index < plans.size(); index += 1) {
            Object item = plans.get(index);
            if (!(item instanceof Map<?, ?> planRaw)) {
                continue;
            }
            Map<String, Object> plan = (Map<String, Object>) planRaw;
            String key = normalizeKey(asString(plan.get("key")));
            if (key == null || key.isBlank()) {
                continue;
            }
            Object value = resolveTempVarPlan(plan, context, resolved, orderByKey, index);
            resolved.put(key, value);
        }

        return resolved;
    }

    private Object resolveTempVarPlan(Map<String, Object> plan,
            FlowContext context,
            Map<String, Object> resolvedTempVars,
            Map<String, Integer> orderByKey,
            int currentIndex) {
        String kind = asString(plan.get("kind"));
        if (kind == null || kind.isBlank()) {
            kind = "ctx";
        }
        if ("expression".equals(kind)) {
            return evaluateExpression(plan, context, resolvedTempVars);
        }
        if ("tempVar".equals(kind)) {
            String tempKey = normalizeTempVarPath(asString(plan.get("tempKey")));
            String rootKey = firstSegment(tempKey);
            if (rootKey == null || rootKey.isBlank()) {
                log.warn("[BranchNodeExecutor] branch tempVar has empty tempKey, plan={}", plan);
                return null;
            }
            if (!resolvedTempVars.containsKey(rootKey)) {
                Integer targetIndex = orderByKey.get(rootKey);
                if (targetIndex != null && targetIndex >= currentIndex) {
                    log.warn("[BranchNodeExecutor] branch tempVar '{}' references a later tempVar '{}', ignoring",
                            normalizeKey(asString(plan.get("key"))), tempKey);
                } else {
                    log.warn("[BranchNodeExecutor] branch tempVar '{}' references missing tempVar '{}', ignoring",
                            normalizeKey(asString(plan.get("key"))), tempKey);
                }
                return null;
            }
            plan = new LinkedHashMap<>(plan);
            plan.put("tempKey", tempKey);
        }
        if ("ctx".equals(kind) && !plan.containsKey("kind")) {
            return JsonPathUtil.extract(context.getVariables(), asString(plan.get("path")));
        }
        return DynamicValueResolver.resolveSource(plan, context, resolvedTempVars, applicationContext);
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

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeTempVarPath(String value) {
        String normalized = normalizeKey(value);
        if (normalized == null) {
            return null;
        }
        return normalized.startsWith("temp.") ? normalized.substring("temp.".length()) : normalized;
    }

    private String firstSegment(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int dotIndex = value.indexOf('.');
        if (dotIndex < 0) {
            return value;
        }
        return value.substring(0, dotIndex);
    }
}

package org.yglue.flow.runtime.core.engine.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.engine.interceptors.JsonPathUtil;
import org.yglue.flow.runtime.core.engine.support.DynamicValueResolver;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BranchConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(BranchConditionEvaluator.class);

    @SuppressWarnings("unchecked")
    public static boolean evaluate(Map<String, Object> conditionTree,
            FlowContext context,
            Map<String, Object> tempVars) {
        return evaluate(conditionTree, context, tempVars, null);
    }

    @SuppressWarnings("unchecked")
    public static boolean evaluate(Map<String, Object> conditionTree,
            FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        if (conditionTree == null || conditionTree.isEmpty()) {
            return true;
        }

        String groupOp = (String) conditionTree.get("op");
        List<Map<String, Object>> rules = (List<Map<String, Object>>) conditionTree.get("rules");
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        boolean isOr = "or".equalsIgnoreCase(groupOp);
        for (Map<String, Object> rule : rules) {
            boolean ruleResult = evaluateRule(rule, context, tempVars, applicationContext);
            if (isOr) {
                if (ruleResult) {
                    return true;
                }
            } else if (!ruleResult) {
                return false;
            }
        }

        return !isOr;
    }

    @SuppressWarnings("unchecked")
    private static boolean evaluateRule(Map<String, Object> rule,
            FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        String op = (String) rule.get("op");
        Map<String, Object> leftAst = (Map<String, Object>) rule.get("left");
        Map<String, Object> rightAst = (Map<String, Object>) rule.get("right");

        Object leftVal = extractValue(leftAst, context, tempVars, applicationContext);
        Object rightVal = extractValue(rightAst, context, tempVars, applicationContext);

        if (op == null) {
            op = "eq";
        }

        try {
            return switch (op.toLowerCase()) {
                case "eq" -> Objects.equals(leftVal, rightVal)
                        || Objects.equals(String.valueOf(leftVal), String.valueOf(rightVal));
                case "ne" -> !Objects.equals(leftVal, rightVal)
                        && !Objects.equals(String.valueOf(leftVal), String.valueOf(rightVal));
                case "gt" -> compareNumbers(leftVal, rightVal) > 0;
                case "ge" -> compareNumbers(leftVal, rightVal) >= 0;
                case "lt" -> compareNumbers(leftVal, rightVal) < 0;
                case "le" -> compareNumbers(leftVal, rightVal) <= 0;
                case "contains" -> leftVal != null
                        && rightVal != null
                        && String.valueOf(leftVal).contains(String.valueOf(rightVal));
                case "in" -> containsValue(leftVal, rightVal);
                default -> {
                    log.warn("[BranchConditionEvaluator] unknown operator {}, treating as false", op);
                    yield false;
                }
            };
        } catch (Exception ex) {
            log.warn("[BranchConditionEvaluator] rule eval failed: op={}, left={}, right={}",
                    op, leftVal, rightVal, ex);
            return false;
        }
    }

    private static boolean containsValue(Object leftVal, Object rightVal) {
        if (leftVal == null) {
            return false;
        }
        if (rightVal instanceof Collection<?> collection) {
            return collection.contains(leftVal);
        }
        if (rightVal instanceof String text) {
            for (String item : text.split(",")) {
                if (item.trim().equals(String.valueOf(leftVal))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Object extractValue(Map<String, Object> sourceAst,
            FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        if (sourceAst == null) {
            return null;
        }
        String kind = sourceAst.get("kind") == null ? "const" : String.valueOf(sourceAst.get("kind"));
        return switch (kind) {
            case "ctx" -> JsonPathUtil.extract(context.getVariables(), String.valueOf(sourceAst.get("path")));
            case "const" -> sourceAst.get("constValue");
            case "tempVar" -> JsonPathUtil.extract(tempVars, String.valueOf(sourceAst.get("tempKey")));
            case "serviceCall" -> DynamicValueResolver.resolveSource(sourceAst, context, tempVars, applicationContext);
            default -> null;
        };
    }

    private static int compareNumbers(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }

        try {
            BigDecimal bLeft = new BigDecimal(String.valueOf(left));
            BigDecimal bRight = new BigDecimal(String.valueOf(right));
            return bLeft.compareTo(bRight);
        } catch (NumberFormatException ex) {
            return String.valueOf(left).compareTo(String.valueOf(right));
        }
    }
}

package org.yglue.flow.runtime.core.engine.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.engine.interceptors.JsonPathUtil;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分支条件 AST 规则树执行器
 * 针对前端 BranchConditionBuilder 产出的深度 JSON AST 结构（And/Or, eq/ne/gt...）进行内存级别极速运算，
 * 达到完全去除 Groovy 编译损耗和安全隐患的目的。
 */
public class BranchConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(BranchConditionEvaluator.class);

    @SuppressWarnings("unchecked")
    public static boolean evaluate(Map<String, Object> conditionTree, FlowContext context,
            Map<String, Object> tempVars) {
        if (conditionTree == null || conditionTree.isEmpty()) {
            return true; // 默认放行
        }

        String groupOp = (String) conditionTree.get("op");
        List<Map<String, Object>> rules = (List<Map<String, Object>>) conditionTree.get("rules");

        if (rules == null || rules.isEmpty()) {
            return true;
        }

        boolean isOr = "or".equalsIgnoreCase(groupOp);

        for (Map<String, Object> rule : rules) {
            boolean ruleResult = evaluateRule(rule, context, tempVars);
            if (isOr) {
                if (ruleResult)
                    return true; // OR 只要有一个为真即为真
            } else {
                if (!ruleResult)
                    return false; // AND 只要有一个为假即为假
            }
        }

        return !isOr; // 如果是 OR，经过上面循环没返回 true 就代表全是 false；如果是 AND 就代表全是 true
    }

    @SuppressWarnings("unchecked")
    private static boolean evaluateRule(Map<String, Object> rule, FlowContext context, Map<String, Object> tempVars) {
        String op = (String) rule.get("op");
        Map<String, Object> leftAst = (Map<String, Object>) rule.get("left");
        Map<String, Object> rightAst = (Map<String, Object>) rule.get("right");

        Object leftVal = extractValue(leftAst, context, tempVars);
        Object rightVal = extractValue(rightAst, context, tempVars);

        if (op == null)
            op = "eq";

        try {
            switch (op.toLowerCase()) {
                case "eq":
                    return Objects.equals(leftVal, rightVal)
                            || Objects.equals(String.valueOf(leftVal), String.valueOf(rightVal));
                case "ne":
                    return !Objects.equals(leftVal, rightVal)
                            && !Objects.equals(String.valueOf(leftVal), String.valueOf(rightVal));
                case "gt":
                    return compareNumbers(leftVal, rightVal) > 0;
                case "ge":
                    return compareNumbers(leftVal, rightVal) >= 0;
                case "lt":
                    return compareNumbers(leftVal, rightVal) < 0;
                case "le":
                    return compareNumbers(leftVal, rightVal) <= 0;
                case "contains":
                    if (leftVal == null || rightVal == null)
                        return false;
                    return String.valueOf(leftVal).contains(String.valueOf(rightVal));
                case "in":
                    if (leftVal == null)
                        return false;
                    if (rightVal instanceof Collection) {
                        return ((Collection<?>) rightVal).contains(leftVal);
                    }
                    if (rightVal instanceof String) {
                        for (String item : ((String) rightVal).split(",")) {
                            if (item.trim().equals(String.valueOf(leftVal)))
                                return true;
                        }
                    }
                    return false;
                default:
                    log.warn("[BranchConditionEvaluator] Unknown operator {}, treating as false", op);
                    return false;
            }
        } catch (Exception e) {
            log.warn("[BranchConditionEvaluator] Rule eval failed: op={}, left={}, right={}", op, leftVal, rightVal, e);
            return false;
        }
    }

    private static Object extractValue(Map<String, Object> sourceAst, FlowContext context,
            Map<String, Object> tempVars) {
        if (sourceAst == null)
            return null;
        String kind = (String) sourceAst.get("kind");
        if (kind == null)
            kind = "const";

        switch (kind) {
            case "ctx":
                return JsonPathUtil.extract(context.getVariables(), (String) sourceAst.get("path"));
            case "const":
                return sourceAst.get("constValue");
            case "tempVar":
                return JsonPathUtil.extract(tempVars, (String) sourceAst.get("tempKey"));
            // serviceCall 在执行到分支条件时尚不完善，且有网络开销副作用，如果配置了可自行实现
            default:
                return null;
        }
    }

    private static int compareNumbers(Object left, Object right) {
        if (left == null && right == null)
            return 0;
        if (left == null)
            return -1;
        if (right == null)
            return 1;

        try {
            BigDecimal bLeft = new BigDecimal(String.valueOf(left));
            BigDecimal bRight = new BigDecimal(String.valueOf(right));
            return bLeft.compareTo(bRight);
        } catch (NumberFormatException e) {
            // 降级使用字符串比对
            return String.valueOf(left).compareTo(String.valueOf(right));
        }
    }
}

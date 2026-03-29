package org.yglue.flow.runtime.core.engine.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.engine.interceptors.JsonPathUtil;
import org.yglue.flow.runtime.core.engine.support.DynamicValueResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集合管线执行器
 * 负责解析从 ParamPlans 中下发的 "listPipeline" AST 指令，
 * 纯内存级别模拟实现 collection 的 filter, map 处理与回卷组合。
 */
public class ListPipelineEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ListPipelineEvaluator.class);
    private static final ExpressionParser spelParser = new SpelExpressionParser();

    @SuppressWarnings("unchecked")
    public static Object evaluate(Map<String, Object> plan, FlowContext context, Map<String, Object> tempVars) {
        return evaluate(plan, context, tempVars, null);
    }

    @SuppressWarnings("unchecked")
    public static Object evaluate(Map<String, Object> plan,
            FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        if (plan == null)
            return new ArrayList<>();

        Map<String, Object> listInput = (Map<String, Object>) plan.get("listInput");
        Object sourceCollection = resolveSource(listInput, context, tempVars, applicationContext);

        if (!(sourceCollection instanceof Collection)) {
            if (sourceCollection == null) {
                return new ArrayList<>();
            }
            // 封装为空集合的单元素集合
            List<Object> temp = new ArrayList<>();
            temp.add(sourceCollection);
            sourceCollection = temp;
        }

        List<Object> currentList = new ArrayList<>((Collection<?>) sourceCollection);

        // 1. 处理管线步骤 (filter, map 等)
        List<Map<String, Object>> steps = (List<Map<String, Object>>) plan.get("listSteps");
        if (steps != null) {
            for (Map<String, Object> step : steps) {
                currentList = applyStep(currentList, step, context, tempVars);
            }
        }

        // 2. 处理对象组合 (Compose)
        Map<String, Object> compose = (Map<String, Object>) plan.get("listCompose");
        if (compose != null) {
            currentList = applyCompose(currentList, compose, context, tempVars, applicationContext);
        }

        return currentList;
    }

    private static List<Object> applyStep(List<Object> list, Map<String, Object> step, FlowContext context,
            Map<String, Object> tempVars) {
        String op = (String) step.get("op");
        if ("filter".equals(op)) {
            String expr = prepareSpel((String) step.get("exprText"));
            if (expr == null || expr.isBlank())
                return list;

            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                Object evalRes = evaluateItemExpr(expr, item, context, tempVars);
                boolean isMatch = false;
                if (evalRes instanceof Boolean) {
                    isMatch = (Boolean) evalRes;
                } else if (evalRes != null) {
                    isMatch = Boolean.parseBoolean(evalRes.toString());
                }

                if (isMatch) {
                    result.add(item);
                }
            }
            return result;
        } else if ("map".equals(op)) {
            String expr = prepareSpel((String) step.get("exprText"));
            if (expr == null || expr.isBlank())
                return list;

            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(evaluateItemExpr(expr, item, context, tempVars));
            }
            return result;
        }
        // TODO: enrich, groupBy, reduce
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> applyCompose(List<Object> list, Map<String, Object> compose, FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        List<Map<String, Object>> fields = (List<Map<String, Object>>) compose.get("fields");
        if (fields == null || fields.isEmpty()) {
            return list;
        }

        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> composedItem = new LinkedHashMap<>();
            for (Map<String, Object> field : fields) {
                String targetField = (String) field.get("targetField");
                if (targetField == null || targetField.isBlank())
                    continue;

                Map<String, Object> source = (Map<String, Object>) field.get("source");
                Object val = resolveItemSource(source, item, context, tempVars, applicationContext);

                deepPut(composedItem, targetField, val);
            }
            result.add(composedItem);
        }
        return result;
    }

    private static String prepareSpel(String expr) {
        if (expr == null)
            return null;
        // 把前端常见的 JSONPath 根节点引用转为 SpEL 当前对象的属性访问
        // 如 $.age -> age (因为将 item 当做 Root Object 计算)
        String normalized = expr.trim();
        normalized = normalized.replace("$.", "");
        normalized = normalized.replaceAll("(?<![#\\\\w])item\\.", "#item.");
        normalized = normalized.replaceAll("(?<![#\\\\w])item\\[", "#item[");
        normalized = normalized.replaceAll("(?<![#\\\\w])item(?![\\\\w])", "#item");
        normalized = normalized.replaceAll("(?<![#\\\\w])temp\\.", "#temp.");
        normalized = normalized.replaceAll("(?<![#\\\\w])request\\.", "#request.");
        normalized = normalized.replaceAll("(?<![#\\\\w])context\\.", "#context.");
        normalized = normalized.replaceAll("(?<![#\\\\w])ctx\\.", "#ctx.");
        normalized = normalized.replaceAll("(?<![#\\\\w])nodeOutput\\.", "#nodeOutput.");
        return normalized;
    }

    private static Object evaluateItemExpr(String expr, Object item, FlowContext context,
            Map<String, Object> tempVars) {
        try {
            StandardEvaluationContext spelCtx = new StandardEvaluationContext(item);
            spelCtx.addPropertyAccessor(new MapAccessor());
            Map<String, Object> variables = context == null ? Map.of() : context.getVariables();
            spelCtx.setVariables(variables);
            spelCtx.setVariable("item", item);
            spelCtx.setVariable("temp", tempVars);
            spelCtx.setVariable("context", variables);
            spelCtx.setVariable("ctx", variables);
            Object request = variables.get("request");
            spelCtx.setVariable("request", request != null ? request : variables);
            spelCtx.setVariable("nodeOutput", variables.get("nodeOutput"));
            return spelParser.parseExpression(expr).getValue(spelCtx);
        } catch (Exception e) {
            log.warn("[ListPipelineEvaluator] Item SpEL evaluation failed: {}", expr, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object resolveItemSource(Map<String, Object> source, Object item, FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        if (source == null)
            return null;
        String kind = (String) source.get("kind");
        if (kind == null)
            kind = "const";

        if ("ctx".equals(kind)) {
            String path = (String) source.get("path");
            if (path != null && path.startsWith("$.")) {
                path = path.substring(2);
            }
            if (item instanceof Map) {
                return JsonPathUtil.extract((Map<String, Object>) item, path);
            } else if (item != null) {
                try {
                    // 退化兜底
                    java.lang.reflect.Field field = item.getClass().getDeclaredField(path);
                    field.setAccessible(true);
                    return field.get(item);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        } else if ("tempVar".equals(kind)) {
            return JsonPathUtil.extract(tempVars, (String) source.get("tempKey"));
        } else if ("const".equals(kind)) {
            return source.get("constValue");
        } else if ("serviceCall".equals(kind)) {
            return DynamicValueResolver.resolveSource(source, context, tempVars, applicationContext);
        }
        return null; // serviceCall 目前在列表中不直接支持
    }

    private static Object resolveSource(Map<String, Object> source,
            FlowContext context,
            Map<String, Object> tempVars,
            ApplicationContext applicationContext) {
        if (source == null)
            return null;
        String kind = (String) source.get("kind");
        if (kind == null)
            kind = "const";

        if ("ctx".equals(kind)) {
            return JsonPathUtil.extract(context.getVariables(), (String) source.get("path"));
        } else if ("tempVar".equals(kind)) {
            return JsonPathUtil.extract(tempVars, (String) source.get("tempKey"));
        } else if ("const".equals(kind)) {
            return source.get("constValue");
        } else if ("serviceCall".equals(kind)) {
            return DynamicValueResolver.resolveSource(source, context, tempVars, applicationContext);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void deepPut(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object child = current.get(part);
            if (!(child instanceof Map)) {
                child = new LinkedHashMap<String, Object>();
                current.put(part, child);
            }
            current = (Map<String, Object>) child;
        }
        current.put(parts[parts.length - 1], value);
    }
}

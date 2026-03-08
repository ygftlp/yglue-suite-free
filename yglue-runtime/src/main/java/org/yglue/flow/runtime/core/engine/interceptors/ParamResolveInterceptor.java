package org.yglue.flow.runtime.core.engine.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.yglue.flow.runtime.core.definition.FlowNode;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.engine.DefaultNodeInterceptorChain;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.engine.NodeInterceptor;
import org.yglue.flow.runtime.core.engine.NodeInterceptorChain;
import org.yglue.flow.runtime.core.engine.evaluator.ListPipelineEvaluator;
import org.yglue.flow.runtime.rest.HttpRestInvocationStrategy;
import org.yglue.flow.runtime.rest.RestInvocationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数解析拦截器
 * 负责在节点真正执行前，替代早期 Groovy 脚本直接将上下文数据（ctx）预处理为结构化参数对象。
 * 此时处理完成的数据将被置于 NodeConfig 或上下文中，供具体 Executor 消费。
 */
public class ParamResolveInterceptor implements NodeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ParamResolveInterceptor.class);
    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Override
    @SuppressWarnings("unchecked")
    public Object intercept(FlowContext context, FlowNode unused, NodeInterceptorChain chain) throws Exception {
        NodeDefinition node = ((DefaultNodeInterceptorChain) chain).getNodeDefinition();
        Map<String, Object> config = node.getConfig();
        if (config == null) {
            return chain.proceed();
        }

        Object paramsObj = config.get("paramPlans");
        if (paramsObj instanceof Map) {
            Map<String, Object> paramPlans = (Map<String, Object>) paramsObj;
            List<Map<String, Object>> tempPlans = (List<Map<String, Object>>) paramPlans.get("tempPlans");
            List<Map<String, Object>> argPlans = (List<Map<String, Object>>) paramPlans.get("argPlans");

            Map<String, Object> tempVars = (Map<String, Object>) context.getVariables().computeIfAbsent("_tempVars",
                    k -> new LinkedHashMap<>());

            // Evaluate tempPlans
            if (tempPlans != null) {
                for (Map<String, Object> plan : tempPlans) {
                    String key = (String) plan.get("key");
                    if (key != null && !key.isBlank()) {
                        Object val = resolvePlanSource(plan, context, tempVars);
                        tempVars.put(key, val);
                    }
                }
            }

            // Evaluate argPlans into a deeply composed map
            Map<String, Object> argsMap = new LinkedHashMap<>();
            if (argPlans != null) {
                for (Map<String, Object> plan : argPlans) {
                    String target = (String) plan.get("target");
                    if (target != null && !target.isBlank()) {
                        Object val = resolvePlanSource(plan, context, tempVars);
                        deepPut(argsMap, target, val);
                    }
                }
            }

            // Align composed argsMap into sequential List based on declared inputs
            List<Object> resolvedArgs = new ArrayList<>();
            Object inputsObj = config.get("inputs");
            if (inputsObj instanceof List) {
                List<?> inputs = (List<?>) inputsObj;
                for (Object inputObj : inputs) {
                    if (inputObj instanceof Map) {
                        String name = (String) ((Map<?, ?>) inputObj).get("name");
                        resolvedArgs.add(argsMap.get(name));
                    } else {
                        resolvedArgs.add(null);
                    }
                }
            } else {
                // Fallback to purely argsMap values if inputs declaration is missing
                resolvedArgs.addAll(argsMap.values());
            }

            config.put("_resolvedArgs", resolvedArgs);
        }

        return chain.proceed();
    }

    @SuppressWarnings("unchecked")
    private void deepPut(Map<String, Object> map, String path, Object value) {
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

    @SuppressWarnings("unchecked")
    private Object resolvePlanSource(Map<String, Object> plan, FlowContext context, Map<String, Object> tempVars) {
        if (plan == null)
            return null;

        Map<String, Object> source = (Map<String, Object>) plan.get("source");
        if (source == null)
            return null;

        String kind = (String) source.get("kind");
        if (kind == null)
            kind = "const";

        if ("ctx".equals(kind)) {
            String path = (String) source.get("path");
            return JsonPathUtil.extract(context.getVariables(), path);
        } else if ("tempVar".equals(kind)) {
            String tempKey = (String) source.get("tempKey");
            return JsonPathUtil.extract(tempVars, tempKey);
        } else if ("const".equals(kind)) {
            return source.get("constValue");
        } else if ("expr".equals(kind)) {
            // 轻量级 SpEL 求值兜底
            String expr = (String) source.get("value");
            if (expr == null || expr.isBlank())
                return null;
            StandardEvaluationContext spelCtx = new StandardEvaluationContext();
            spelCtx.setVariables(context.getVariables());
            try {
                return spelParser.parseExpression(expr).getValue(spelCtx);
            } catch (Exception e) {
                log.warn("[ParamResolveInterceptor] SpEL 计算失败: {}", expr, e);
                return null;
            }
        } else if ("listPipeline".equals(kind)) {
            return ListPipelineEvaluator.evaluate(plan, context, tempVars);
        } else if ("httpCall".equals(kind)) {
            Object httpCallCfg = source.get("httpCall");
            if (httpCallCfg instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) map;
                HttpRestInvocationStrategy strategy = new HttpRestInvocationStrategy();
                RestInvocationContext ric = new RestInvocationContext(null, context, config, null);
                try {
                    return strategy.invoke(ric);
                } catch (Exception e) {
                    log.error("[ParamResolveInterceptor] httpCall 失败: {}", config, e);
                    return null;
                }
            }
        }

        // serviceCall 等复杂组装暂由后续完善
        return null;
    }
}

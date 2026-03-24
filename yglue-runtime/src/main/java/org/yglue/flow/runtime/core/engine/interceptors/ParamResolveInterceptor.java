package org.yglue.flow.runtime.core.engine.interceptors;

import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.engine.NodeInterceptor;
import org.yglue.flow.runtime.core.engine.NodeInterceptorChain;
import org.yglue.flow.runtime.core.engine.evaluator.ListPipelineEvaluator;
import org.yglue.flow.runtime.core.engine.support.DynamicValueResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParamResolveInterceptor implements NodeInterceptor {

    private final ApplicationContext applicationContext;

    public ParamResolveInterceptor() {
        this(null);
    }

    public ParamResolveInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object intercept(FlowContext context, NodeDefinition node, NodeInterceptorChain chain) throws Exception {
        Map<String, Object> config = node.getConfig();
        if (config == null) {
            return chain.proceed();
        }

        Object paramsObj = config.get("paramPlans");
        if (paramsObj instanceof Map<?, ?> rawParamPlans) {
            Map<String, Object> paramPlans = (Map<String, Object>) rawParamPlans;
            List<Map<String, Object>> tempPlans = (List<Map<String, Object>>) paramPlans.get("tempPlans");
            List<Map<String, Object>> argPlans = (List<Map<String, Object>>) paramPlans.get("argPlans");

            Map<String, Object> tempVars = (Map<String, Object>) context.getVariables()
                    .computeIfAbsent("_tempVars", key -> new LinkedHashMap<>());

            if (tempPlans != null) {
                for (Map<String, Object> plan : tempPlans) {
                    String key = (String) plan.get("key");
                    if (key != null && !key.isBlank()) {
                        tempVars.put(key, resolvePlanSource(plan, context, tempVars));
                    }
                }
            }

            Map<String, Object> argsMap = new LinkedHashMap<>();
            if (argPlans != null) {
                for (Map<String, Object> plan : argPlans) {
                    String target = (String) plan.get("target");
                    if (target != null && !target.isBlank()) {
                        deepPut(argsMap, target, resolvePlanSource(plan, context, tempVars));
                    }
                }
            }

            List<Object> resolvedArgs = new ArrayList<>();
            Object inputsObj = config.get("inputs");
            if (inputsObj instanceof List<?> inputs) {
                for (Object inputObj : inputs) {
                    if (inputObj instanceof Map<?, ?> inputMap) {
                        resolvedArgs.add(argsMap.get(String.valueOf(inputMap.get("name"))));
                    } else {
                        resolvedArgs.add(null);
                    }
                }
            } else {
                resolvedArgs.addAll(argsMap.values());
            }

            context.setResolvedArgs(node.getId(), resolvedArgs);
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
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                current.put(part, child);
            }
            current = (Map<String, Object>) child;
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private Object resolvePlanSource(Map<String, Object> plan, FlowContext context, Map<String, Object> tempVars) {
        if (plan == null) {
            return null;
        }

        Map<String, Object> source = (Map<String, Object>) plan.get("source");
        if (source == null) {
            return null;
        }

        String kind = (String) source.getOrDefault("kind", "const");
        if ("ctx".equals(kind)) {
            return JsonPathUtil.extract(context.getVariables(), (String) source.get("path"));
        }
        if ("tempVar".equals(kind)) {
            return JsonPathUtil.extract(tempVars, (String) source.get("tempKey"));
        }
        if ("const".equals(kind)) {
            return source.get("constValue");
        }
        if ("expr".equals(kind)) {
            return DynamicValueResolver.resolveSource(source, context, tempVars, applicationContext);
        }
        if ("listPipeline".equals(kind)) {
            return ListPipelineEvaluator.evaluate(plan, context, tempVars, applicationContext);
        }
        if ("httpCall".equals(kind)) {
            return DynamicValueResolver.resolveSource(source, context, tempVars, applicationContext);
        }
        if ("serviceCall".equals(kind)) {
            return DynamicValueResolver.resolveSource(source, context, tempVars, applicationContext);
        }

        return null;
    }
}

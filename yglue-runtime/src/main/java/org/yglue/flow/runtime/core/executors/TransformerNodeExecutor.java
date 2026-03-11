package org.yglue.flow.runtime.core.executors;

import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.script.GroovyScriptEngine;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;
import org.yglue.flow.runtime.core.util.ParamResolver;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes transformer nodes backed by field mappings and optional scripts.
 */
public class TransformerNodeExecutor implements FlowExecutor {

    private static final GroovyScriptEngine GROOVY_SCRIPT_ENGINE = GroovyScriptEngine.shared();

    @Override
    public Object execute(NodeExecutionContext context) throws Exception {
        Map<String, Object> config = context.getNode().getConfig();
        FlowContext flowContext = context.getContext();

        Object input = getUpstreamOutput(flowContext, config);

        String outputType = getString(config, "outputType");
        boolean isSingleValue = "single".equals(outputType);

        Object mappingResult = applyFieldMappings(config, flowContext, input, isSingleValue);

        Map<String, Object> output;
        Object singleValue = null;
        if (isSingleValue && !(mappingResult instanceof Map)) {
            singleValue = mappingResult;
            output = new LinkedHashMap<>();
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapResult = mappingResult instanceof Map
                    ? (Map<String, Object>) mappingResult
                    : new LinkedHashMap<>();
            output = mapResult;
        }

        String script = getString(config, "script");
        if (script != null && !script.trim().isEmpty()) {
            Map<String, Object> scriptOutput = executeGroovyScript(script, flowContext, input, output);

            if (isSingleValue && singleValue != null) {
                if (scriptOutput != null && scriptOutput.size() == 1) {
                    Object scriptValue = scriptOutput.values().iterator().next();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mappingConfig = (Map<String, Object>) config.get("mappingConfig");
                    if (mappingConfig != null) {
                        Object fieldMappingsObj = mappingConfig.get("fieldMappings");
                        if (fieldMappingsObj instanceof List && !((List<?>) fieldMappingsObj).isEmpty()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> firstMapping = (Map<String, Object>) ((List<?>) fieldMappingsObj).get(0);
                            String target = getString(firstMapping, "targetField");
                            if (target == null || target.isEmpty()) {
                                singleValue = scriptValue;
                                output = new LinkedHashMap<>();
                            } else {
                                output = scriptOutput;
                            }
                        }
                    }
                } else {
                    output = scriptOutput;
                }
            } else {
                output = scriptOutput;
            }
        }

        if (isSingleValue && singleValue != null) {
            String alias = getString(config, "as");
            if (alias != null && !alias.isEmpty()) {
                flowContext.set(alias, singleValue);
            }
            flowContext.setReturnValue(singleValue);
            return singleValue;
        }

        String alias = getString(config, "as");
        if (alias != null && !alias.isEmpty()) {
            flowContext.set(alias, output);
        }
        flowContext.setReturnValue(output);

        return output;
    }

    /**
     * Returns the default input for a transformer node.
     */
    private Object getUpstreamOutput(FlowContext context, Map<String, Object> config) {
        Map<String, Object> data = context.data();

        Object inputSourceObj = config.get("inputSource");
        if (inputSourceObj != null) {
            String inputSource = String.valueOf(inputSourceObj);
            if (!inputSource.isEmpty() && !inputSource.equals("null")) {
                Map<String, Object> resolverConfig = new LinkedHashMap<>();
                resolverConfig.put("type", "context");
                resolverConfig.put("path", inputSource);
                Object value = ParamResolver.resolve(resolverConfig, context);
                if (value != null) {
                    return value;
                }
            }
        }

        Object retValue = data.get("ret");
        if (retValue != null) {
            return retValue;
        }

        Object lastOutput = null;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_") || key.equals("request")) {
                continue;
            }
            Object value = entry.getValue();
            if (value != null && !(value instanceof String && ((String) value).isEmpty())) {
                lastOutput = value;
            }
        }

        return lastOutput != null ? lastOutput : data;
    }

    /**
     * Applies the configured field mappings.
     */
    @SuppressWarnings("unchecked")
    private Object applyFieldMappings(Map<String, Object> config,
                                     FlowContext context,
                                     Object input,
                                     boolean isSingleValue) {
        Map<String, Object> output = new LinkedHashMap<>();

        Object mappingConfigObj = config.get("mappingConfig");
        if (!(mappingConfigObj instanceof Map)) {
            return output;
        }

        Map<String, Object> mappingConfig = (Map<String, Object>) mappingConfigObj;
        Object fieldMappingsObj = mappingConfig.get("fieldMappings");
        if (!(fieldMappingsObj instanceof List)) {
            return output;
        }

        List<Map<String, Object>> fieldMappings = (List<Map<String, Object>>) fieldMappingsObj;

        if (isSingleValue && !fieldMappings.isEmpty()) {
            Map<String, Object> firstMapping = fieldMappings.get(0);
            String sourceField = getString(firstMapping, "sourceField");
            String targetField = getString(firstMapping, "targetField");
            String transformation = getString(firstMapping, "transformation");
            String defaultValue = getString(firstMapping, "defaultValue");

            Object value = null;

            if ("constant".equals(transformation)) {
                value = defaultValue;
            } else if ("script".equals(transformation)) {
                String script = getString(firstMapping, "script");
                if (script != null && !script.trim().isEmpty()) {
                    value = evaluateScript(script, context, input);
                } else {
                    value = defaultValue;
                }
            } else {
                if (sourceField != null && !sourceField.isEmpty()) {
                    value = getNestedValue(input, sourceField);
                    if (value == null) {
                        value = getNestedValue(context.data(), sourceField);
                    }
                } else {
                    value = input;
                }

                if (value == null && defaultValue != null && !defaultValue.isEmpty()) {
                    value = defaultValue;
                }
            }

            if (targetField == null || targetField.isEmpty()) {
                return value != null ? value : defaultValue;
            } else {
                output.put(targetField, value);
                return output;
            }
        }

        for (Map<String, Object> mapping : fieldMappings) {
            String sourceField = getString(mapping, "sourceField");
            String targetField = getString(mapping, "targetField");
            String transformation = getString(mapping, "transformation");
            String defaultValue = getString(mapping, "defaultValue");

            if (targetField == null || targetField.isEmpty()) {
                continue;
            }

            Object value = null;

            if ("constant".equals(transformation)) {
                value = defaultValue;
            } else if ("script".equals(transformation)) {
                String script = getString(mapping, "script");
                if (script != null && !script.trim().isEmpty()) {
                    value = evaluateScript(script, context, input);
                } else {
                    value = defaultValue;
                }
            } else {
                if (sourceField != null && !sourceField.isEmpty()) {
                    value = getNestedValue(input, sourceField);
                    if (value == null) {
                        value = getNestedValue(context.data(), sourceField);
                    }
                } else {
                    value = input;
                }

                if (value == null && defaultValue != null && !defaultValue.isEmpty()) {
                    value = defaultValue;
                }
            }

            if (value != null) {
                setNestedValue(output, targetField, value);
            }
        }

        return output;
    }

    /**
     * Executes the configured Groovy script.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeGroovyScript(String script,
                                                    FlowContext context,
                                                    Object input,
                                                    Map<String, Object> output) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("ctx", context.data());
            variables.put("input", input);
            variables.put("output", output);

            Object result = GROOVY_SCRIPT_ENGINE.evaluate(script, variables);

            if (result instanceof Map) {
                return (Map<String, Object>) result;
            } else if (result != null) {
                Map<String, Object> wrapped = new LinkedHashMap<>();
                wrapped.put("result", result);
                return wrapped;
            }

            return output;
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            String helpfulHint = "";
            if (errorMessage != null) {
                if (errorMessage.contains("retrun") && errorMessage.contains("No signature of method")) {
                    helpfulHint = " (Possible typo: did you mean 'return' instead of 'retrun'?)";
                } else if (errorMessage.contains("retun") && errorMessage.contains("No signature of method")) {
                    helpfulHint = " (Possible typo: did you mean 'return' instead of 'retun'?)";
                }
            }
            throw new RuntimeException("Groovy script execution failed: " + errorMessage + helpfulHint
                    + "\nScript content: " + script, e);
        }
    }

    /**
     * Evaluates a simple expression against a temporary context.
     */
    private Object evaluateScript(String script, FlowContext context, Object input) {
        Map<String, Object> evalContext = new HashMap<>(context.data());
        evalContext.put("input", input);

        FlowContext tempContext = new FlowContext();
        tempContext.data().putAll(evalContext);

        return ExpressionEvaluator.evaluate(script, tempContext);
    }

    /**
     * Reads a nested value using dotted paths such as {@code user.name}.
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(Object obj, String path) {
        if (obj == null || path == null || path.isEmpty()) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object current = obj;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                try {
                    java.lang.reflect.Field field = current.getClass().getDeclaredField(part);
                    field.setAccessible(true);
                    current = field.get(current);
                } catch (Exception e) {
                    return null;
                }
            }
        }

        return current;
    }

    /**
     * Writes a nested value using a dotted path.
     */
    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> map, String path, Object value) {
        if (map == null || path == null || path.isEmpty()) {
            return;
        }

        String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<>();
                current.put(part, next);
            }
            current = (Map<String, Object>) next;
        }

        current.put(parts[parts.length - 1], value);
    }

    /**
     * Safe string accessor for config maps.
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}
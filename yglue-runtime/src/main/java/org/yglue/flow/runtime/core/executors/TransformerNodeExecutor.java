package org.yglue.flow.runtime.core.executors;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;
import org.yglue.flow.runtime.core.util.ParamResolver;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 转换器节点执行器
 * 支持字段映射和 Groovy 脚本执行，用于将流程输出转换为目标接口的请求/响应结构
 */
public class TransformerNodeExecutor implements NodeExecutor {

    @Override
    public Object execute(NodeExecutionContext context) throws Exception {
        Map<String, Object> config = context.getNode().getConfig();
        FlowContext flowContext = context.getContext();

        // 获取上游节点的输出（通常是前一个节点的结果）
        Object input = getUpstreamOutput(flowContext, config);

        // 获取输出类型：object（对象）或 single（单值）
        String outputType = getString(config, "outputType");
        boolean isSingleValue = "single".equals(outputType);
        
        // 执行字段映射
        Object mappingResult = applyFieldMappings(config, flowContext, input, isSingleValue);
        
        // 处理单值模式：如果 mappingResult 是单值，直接使用
        Map<String, Object> output;
        Object singleValue = null;
        if (isSingleValue && !(mappingResult instanceof Map)) {
            // 单值模式且返回的是单值
            singleValue = mappingResult;
            output = new LinkedHashMap<>();
        } else {
            // 对象模式或单值模式但返回的是对象
            @SuppressWarnings("unchecked")
            Map<String, Object> mapResult = mappingResult instanceof Map 
                ? (Map<String, Object>) mappingResult 
                : new LinkedHashMap<>();
            output = mapResult;
        }

        // 执行 Groovy 脚本（如果配置了）
        String script = getString(config, "script");
        if (script != null && !script.trim().isEmpty()) {
            Map<String, Object> scriptOutput = executeGroovyScript(script, flowContext, input, output);
            
            if (isSingleValue && singleValue != null) {
                // 单值模式下，如果脚本返回的是 Map，检查是否应该提取单值
                if (scriptOutput != null && scriptOutput.size() == 1) {
                    Object scriptValue = scriptOutput.values().iterator().next();
                    // 检查目标字段是否为空
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

        // 单值模式处理：如果目标字段为空，直接返回单值
        if (isSingleValue && singleValue != null) {
            String alias = getString(config, "as");
            if (alias != null && !alias.isEmpty()) {
                flowContext.set(alias, singleValue);
            }
            flowContext.setReturnValue(singleValue);
            return singleValue;
        }

        // 将结果保存到上下文
        String alias = getString(config, "as");
        if (alias != null && !alias.isEmpty()) {
            flowContext.set(alias, output);
        }
        flowContext.setReturnValue(output);

        return output;
    }

    /**
     * 获取上游节点的输出
     * 转换器的输入来源优先级：
     * 1. _lastNodeResult - 最后一个节点的输出（由 FlowExecutor 自动保存）
     * 2. 节点配置中的 inputSource - 如果配置了特定的输入源路径（如 ctx.xxx）
     * 3. 上下文中的其他值（向后兼容）
     * 
     * @param context 流程上下文
     * @param config 节点配置
     * @return 上游节点的输出
     */
    private Object getUpstreamOutput(FlowContext context, Map<String, Object> config) {
        Map<String, Object> data = context.data();
        
        // 优先级1：从配置中获取指定的输入源路径
        Object inputSourceObj = config.get("inputSource");
        if (inputSourceObj != null) {
            String inputSource = String.valueOf(inputSourceObj);
            if (!inputSource.isEmpty() && !inputSource.equals("null")) {
                // 使用 ParamResolver 解析路径
                Map<String, Object> resolverConfig = new LinkedHashMap<>();
                resolverConfig.put("type", "context");
                resolverConfig.put("path", inputSource);
                Object value = ParamResolver.resolve(resolverConfig, context);
                if (value != null) {
                    return value;
                }
            }
        }
        
        // 优先级2：获取最后一个节点的输出（由 FlowExecutor 自动保存）
        Object lastNodeResult = data.get("_lastNodeResult");
        if (lastNodeResult != null) {
            return lastNodeResult;
        }
        
        // 优先级3：尝试从上下文获取 ret（返回值）
        Object retValue = data.get("ret");
        if (retValue != null) {
            return retValue;
        }
        
        // 优先级4：查找最近的非空输出值（向后兼容）
        Object lastOutput = null;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            // 跳过系统内部使用的key
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
     * 应用字段映射配置
     * 
     * @param config 节点配置
     * @param context 流程上下文
     * @param input 输入数据
     * @param isSingleValue 是否为单值模式
     * @return 映射结果：单值模式下如果目标字段为空返回单值，否则返回 Map
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
        
        // 单值模式：只处理第一个映射
        if (isSingleValue && !fieldMappings.isEmpty()) {
            Map<String, Object> firstMapping = fieldMappings.get(0);
            String sourceField = getString(firstMapping, "sourceField");
            String targetField = getString(firstMapping, "targetField");
            String transformation = getString(firstMapping, "transformation");
            String defaultValue = getString(firstMapping, "defaultValue");
            
            Object value = null;
            
            if ("constant".equals(transformation)) {
                // 常量赋值
                value = defaultValue;
            } else if ("script".equals(transformation)) {
                // 脚本转换（简单脚本，复杂逻辑用 Groovy 脚本）
                String script = getString(firstMapping, "script");
                if (script != null && !script.trim().isEmpty()) {
                    value = evaluateScript(script, context, input);
                } else {
                    value = defaultValue;
                }
            } else {
                // 直接映射
                if (sourceField != null && !sourceField.isEmpty()) {
                    value = getNestedValue(input, sourceField);
                    if (value == null) {
                        value = getNestedValue(context.data(), sourceField);
                    }
                } else {
                    // 没有源字段，使用 input 本身
                    value = input;
                }
                
                // 如果值为空，使用默认值
                if (value == null && defaultValue != null && !defaultValue.isEmpty()) {
                    value = defaultValue;
                }
            }
            
            // 单值模式：如果目标字段为空，直接返回值；否则返回包含该字段的对象
            if (targetField == null || targetField.isEmpty()) {
                return value != null ? value : defaultValue;
            } else {
                // 目标字段不为空，返回包含该字段的对象
                output.put(targetField, value);
                return output;
            }
        }
        
        // 对象模式：处理所有映射
        for (Map<String, Object> mapping : fieldMappings) {
            String sourceField = getString(mapping, "sourceField");
            String targetField = getString(mapping, "targetField");
            String transformation = getString(mapping, "transformation");
            String defaultValue = getString(mapping, "defaultValue");
            
            // 对象模式下，目标字段不能为空
            if (targetField == null || targetField.isEmpty()) {
                continue;
            }
            
            Object value = null;
            
            if ("constant".equals(transformation)) {
                // 常量赋值
                value = defaultValue;
            } else if ("script".equals(transformation)) {
                // 脚本转换（简单脚本，复杂逻辑用 Groovy 脚本）
                String script = getString(mapping, "script");
                if (script != null && !script.trim().isEmpty()) {
                    value = evaluateScript(script, context, input);
                } else {
                    value = defaultValue;
                }
            } else {
                // 直接映射
                if (sourceField != null && !sourceField.isEmpty()) {
                    value = getNestedValue(input, sourceField);
                    if (value == null) {
                        value = getNestedValue(context.data(), sourceField);
                    }
                } else {
                    // 没有源字段，使用 input 本身
                    value = input;
                }
                
                // 如果值为空，使用默认值
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
     * 执行 Groovy 脚本
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeGroovyScript(String script, 
                                                   FlowContext context, 
                                                   Object input, 
                                                   Map<String, Object> output) {
        try {
            Binding binding = new Binding();
            binding.setVariable("ctx", context.data());
            binding.setVariable("input", input);
            binding.setVariable("output", output);
            
            GroovyShell shell = new GroovyShell(binding);
            Object result = shell.evaluate(script);
            
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            } else if (result != null) {
                // 如果脚本返回非 Map 类型，将其包装
                Map<String, Object> wrapped = new LinkedHashMap<>();
                wrapped.put("result", result);
                return wrapped;
            }
            
            return output;
        } catch (Exception e) {
            // 检测常见的拼写错误，提供更友好的错误提示
            String errorMessage = e.getMessage();
            String helpfulHint = "";
            if (errorMessage != null) {
                if (errorMessage.contains("retrun") && errorMessage.contains("No signature of method")) {
                    helpfulHint = " (提示：可能是拼写错误，请检查是否将 'return' 写成了 'retrun')";
                } else if (errorMessage.contains("retun") && errorMessage.contains("No signature of method")) {
                    helpfulHint = " (提示：可能是拼写错误，请检查是否将 'return' 写成了 'retun')";
                }
            }
            throw new RuntimeException("Groovy script execution failed: " + errorMessage + helpfulHint 
                + "\n脚本内容: " + script, e);
        }
    }

    /**
     * 评估简单脚本表达式
     */
    private Object evaluateScript(String script, FlowContext context, Object input) {
        // 使用 SpEL 表达式评估器
        Map<String, Object> evalContext = new HashMap<>(context.data());
        evalContext.put("input", input);
        
        // 创建一个临时的 FlowContext 用于评估
        FlowContext tempContext = new FlowContext();
        tempContext.data().putAll(evalContext);
        
        return ExpressionEvaluator.evaluate(script, tempContext);
    }

    /**
     * 获取嵌套字段值（支持点号分隔的路径，如 "user.name"）
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
                // 尝试使用反射获取属性
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
     * 设置嵌套字段值（支持点号分隔的路径）
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
     * 安全获取字符串值
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}


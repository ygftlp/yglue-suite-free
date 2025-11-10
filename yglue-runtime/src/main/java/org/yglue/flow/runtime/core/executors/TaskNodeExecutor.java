package org.yglue.flow.runtime.core.executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.util.ParamResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务节点执行器
 * 用于执行业务组件（FlowOperation）节点
 * 
 * 节点配置格式：
 * {
 *   "type": "task",
 *   "comp": {
 *     "bean": "bean名称（Service name）",
 *     "method": "方法名",
 *     "configJson": "...", // 可选，包含 flowApiBeanName 等信息
 *     "endpointType": "FLOW_OPERATION"
 *   },
 *   "inputs": [ // 输入参数配置
 *     {
 *       "name": "参数名",
 *       "resolver": { // 参数解析器配置
 *         "type": "request|context|constant|expression",
 *         "path": "...",
 *         "constant": "...",
 *         "expression": "...",
 *         "default": "..."
 *       }
 *     }
 *   ]
 * }
 */
public class TaskNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskNodeExecutor.class);

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    public TaskNodeExecutor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(NodeExecutionContext context) throws Exception {
        String nodeId = context.getNode().getId();
        log.info("[TaskNodeExecutor] 开始执行任务节点: nodeId={}", nodeId);
        
        Map<String, Object> config = context.getNode().getConfig();
        log.debug("[TaskNodeExecutor] 节点配置: nodeId={}, config={}", nodeId, config);
        
        // 获取组件配置
        Object compObj = config.get("comp");
        if (compObj == null) {
            log.error("[TaskNodeExecutor] 任务节点缺少 'comp' 配置: nodeId={}", nodeId);
            throw new IllegalArgumentException("Task node must have 'comp' configuration");
        }
        log.debug("[TaskNodeExecutor] 获取到组件配置: nodeId={}, compObj={}", nodeId, compObj);
        
        Map<String, Object> comp = compObj instanceof Map 
            ? (Map<String, Object>) compObj 
            : objectMapper.convertValue(compObj, Map.class);
        log.debug("[TaskNodeExecutor] 组件配置已转换: nodeId={}, comp={}", nodeId, comp);
        
        // 获取 bean 名称和方法名
        String beanName = getBeanName(comp);
        String methodName = getMethodName(comp);
        log.info("[TaskNodeExecutor] 解析组件信息: nodeId={}, beanName={}, methodName={}", nodeId, beanName, methodName);
        
        if (beanName == null || beanName.isBlank()) {
            log.error("[TaskNodeExecutor] bean 名称为空: nodeId={}, comp={}", nodeId, comp);
            throw new IllegalArgumentException("Task node 'comp.bean' or 'comp.configJson.flowApiBeanName' is required");
        }
        if (methodName == null || methodName.isBlank()) {
            log.error("[TaskNodeExecutor] 方法名为空: nodeId={}, comp={}", nodeId, comp);
            throw new IllegalArgumentException("Task node 'comp.method' is required");
        }
        
        // 从 Spring 容器中获取 bean 的实际类型
        log.debug("[TaskNodeExecutor] 从 Spring 容器获取 bean: nodeId={}, beanName={}", nodeId, beanName);
        Object bean;
        Class<?> beanType;
        try {
            // 先通过 BeanFactory 获取 bean 的实际类型（处理代理类的情况）
            if (applicationContext instanceof BeanFactory) {
                BeanFactory beanFactory = (BeanFactory) applicationContext;
                beanType = beanFactory.getType(beanName);
                log.debug("[TaskNodeExecutor] 从 BeanFactory 获取 bean 类型: nodeId={}, beanName={}, beanType={}", 
                    nodeId, beanName, beanType != null ? beanType.getName() : "null");
            } else {
                beanType = null;
            }
            
            // 获取 bean 实例
            bean = applicationContext.getBean(beanName);
            log.info("[TaskNodeExecutor] 成功获取 bean: nodeId={}, beanName={}, beanClass={}, beanType={}", 
                nodeId, beanName, 
                bean != null ? bean.getClass().getName() : "null",
                beanType != null ? beanType.getName() : "null");
        } catch (Exception e) {
            log.error("[TaskNodeExecutor] 获取 bean 失败: nodeId={}, beanName={}, error={}", 
                nodeId, beanName, e.getMessage(), e);
            throw new IllegalArgumentException("Bean not found: " + beanName, e);
        }
        
        if (bean == null) {
            log.error("[TaskNodeExecutor] bean 为 null: nodeId={}, beanName={}", nodeId, beanName);
            throw new IllegalArgumentException("Bean not found: " + beanName);
        }
        
        // 确定用于查找方法的类：优先使用 BeanFactory 获取的实际类型，否则使用 AopProxyUtils 处理代理类
        Class<?> targetClass;
        if (beanType != null) {
            targetClass = beanType;
            log.debug("[TaskNodeExecutor] 使用 BeanFactory 获取的实际类型: nodeId={}, targetClass={}", 
                nodeId, targetClass.getName());
        } else {
            // 使用 AopProxyUtils 获取目标类（处理 CGLIB 和 JDK 动态代理）
            targetClass = AopProxyUtils.ultimateTargetClass(bean);
            log.debug("[TaskNodeExecutor] 使用 AopProxyUtils 获取目标类: nodeId={}, targetClass={}, beanClass={}", 
                nodeId, targetClass.getName(), bean.getClass().getName());
        }
        
        // 解析输入参数
        log.debug("[TaskNodeExecutor] 开始解析输入参数: nodeId={}", nodeId);
        List<Object> arguments = new ArrayList<>();
        Object inputsConfig = config.get("inputs");
        if (inputsConfig instanceof List<?> inputs) {
            log.debug("[TaskNodeExecutor] 输入参数配置数量: nodeId={}, count={}", nodeId, inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                Object inputObj = inputs.get(i);
                Map<String, Object> input = inputObj instanceof Map
                    ? (Map<String, Object>) inputObj
                    : objectMapper.convertValue(inputObj, Map.class);
                
                // 使用 ParamResolver 解析参数值
                Object resolverConfig = input.get("resolver");
                log.debug("[TaskNodeExecutor] 解析参数 [{}]: nodeId={}, resolver={}", i, nodeId, resolverConfig);
                Object value = ParamResolver.resolve(resolverConfig, context.getContext());
                log.debug("[TaskNodeExecutor] 参数 [{}] 解析结果: nodeId={}, value={}, valueType={}", 
                    i, nodeId, value, value != null ? value.getClass().getName() : "null");
                arguments.add(value);
            }
        } else {
            log.debug("[TaskNodeExecutor] 无输入参数配置: nodeId={}", nodeId);
        }
        log.info("[TaskNodeExecutor] 输入参数解析完成: nodeId={}, argumentCount={}", nodeId, arguments.size());
        
        // 查找并调用方法（使用实际类型而不是代理类）
        log.debug("[TaskNodeExecutor] 查找方法: nodeId={}, targetClass={}, methodName={}, argumentCount={}", 
            nodeId, targetClass.getName(), methodName, arguments.size());
        Method method = resolveMethod(targetClass, methodName, arguments.size());
        log.info("[TaskNodeExecutor] 找到方法: nodeId={}, method={}", nodeId, method);
        
        log.debug("[TaskNodeExecutor] 转换参数类型: nodeId={}, parameterTypes={}", 
            nodeId, method.getParameterTypes());
        Object[] convertedArgs = convertArgs(method.getParameterTypes(), arguments);
        log.debug("[TaskNodeExecutor] 参数类型转换完成: nodeId={}, convertedArgs={}", nodeId, convertedArgs);
        
        log.info("[TaskNodeExecutor] 开始调用方法: nodeId={}, beanName={}, method={}", 
            nodeId, beanName, method.getName());
        Object result;
        try {
            result = method.invoke(bean, convertedArgs);
            log.info("[TaskNodeExecutor] 方法调用成功: nodeId={}, beanName={}, method={}, resultType={}", 
                nodeId, beanName, method.getName(), result != null ? result.getClass().getName() : "null");
        } catch (Exception e) {
            log.error("[TaskNodeExecutor] 方法调用失败: nodeId={}, beanName={}, method={}, error={}", 
                nodeId, beanName, method.getName(), e.getMessage(), e);
            throw e;
        }
        
        // 将结果保存到上下文（如果有配置）
        Object alias = config.get("as");
        if (alias != null) {
            String aliasStr = String.valueOf(alias);
            log.debug("[TaskNodeExecutor] 保存结果到上下文: nodeId={}, alias={}", nodeId, aliasStr);
            context.getContext().set(aliasStr, result);
        }
        
        log.info("[TaskNodeExecutor] 任务节点执行完成: nodeId={}, beanName={}, method={}", 
            nodeId, beanName, methodName);
        return result;
    }

    /**
     * 获取方法名
     * 固定从 configJson.method 获取，确保是实际的 Java 方法名
     */
    @SuppressWarnings("unchecked")
    private String getMethodName(Map<String, Object> comp) {
        log.debug("[TaskNodeExecutor] 开始获取方法名: comp={}", comp);
        
        Object configJsonObj = comp.get("configJson");
        if (configJsonObj == null) {
            log.warn("[TaskNodeExecutor] configJson 为空，无法获取方法名");
            return null;
        }
        
        try {
            Map<String, Object> configJson;
            if (configJsonObj instanceof String) {
                configJson = (Map<String, Object>) (Map<?, ?>) objectMapper.readValue((String) configJsonObj, Map.class);
            } else if (configJsonObj instanceof Map) {
                configJson = (Map<String, Object>) configJsonObj;
            } else {
                configJson = (Map<String, Object>) (Map<?, ?>) objectMapper.convertValue(configJsonObj, Map.class);
            }
            
            Object method = configJson.get("method");
            if (method != null && !String.valueOf(method).isBlank()) {
                String methodName = String.valueOf(method);
                log.debug("[TaskNodeExecutor] 从 configJson.method 获取方法名: methodName={}", methodName);
                return methodName;
            }
            
            log.warn("[TaskNodeExecutor] configJson 中未找到 method 字段");
            return null;
        } catch (Exception e) {
            log.error("[TaskNodeExecutor] 解析 configJson 失败: error={}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取 bean 名称
     * 固定从 configJson.flowApiBeanName 获取，确保是准确的 Spring bean 名称
     */
    @SuppressWarnings("unchecked")
    private String getBeanName(Map<String, Object> comp) {
        log.debug("[TaskNodeExecutor] 开始获取 bean 名称: comp={}", comp);
        
        Object configJsonObj = comp.get("configJson");
        if (configJsonObj == null) {
            log.warn("[TaskNodeExecutor] configJson 为空，无法获取 bean 名称");
            return null;
        }
        
        try {
            Map<String, Object> configJson;
            if (configJsonObj instanceof String) {
                configJson = (Map<String, Object>) (Map<?, ?>) objectMapper.readValue((String) configJsonObj, Map.class);
            } else if (configJsonObj instanceof Map) {
                configJson = (Map<String, Object>) configJsonObj;
            } else {
                configJson = (Map<String, Object>) (Map<?, ?>) objectMapper.convertValue(configJsonObj, Map.class);
            }
            
            Object flowApiBeanName = configJson.get("flowApiBeanName");
            if (flowApiBeanName != null && !String.valueOf(flowApiBeanName).isBlank()) {
                String beanName = String.valueOf(flowApiBeanName);
                log.debug("[TaskNodeExecutor] 从 configJson.flowApiBeanName 获取 bean 名称: beanName={}", beanName);
                return beanName;
            }
            
            log.warn("[TaskNodeExecutor] configJson 中未找到 flowApiBeanName 字段");
            return null;
        } catch (Exception e) {
            log.error("[TaskNodeExecutor] 解析 configJson 失败: error={}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析方法
     * 根据方法名和参数数量查找匹配的方法
     * 
     * 查找策略：
     * 1. 先查找 public 方法（包括继承的方法）
     * 2. 如果找不到，再查找所有声明的方法（包括 private、protected、package-private）
     * 3. 如果还是找不到，列出所有可用的方法名和参数数量，便于调试
     */
    private Method resolveMethod(Class<?> clazz, String name, int argc) {
        log.debug("[TaskNodeExecutor] 查找方法: clazz={}, name={}, argc={}", clazz.getName(), name, argc);
        
        // 1. 先尝试查找 public 方法（包括继承的方法）
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == argc) {
                log.debug("[TaskNodeExecutor] 找到匹配的 public 方法: method={}, parameterTypes={}", 
                    method, method.getParameterTypes());
                return method;
            }
        }
        
        // 2. 如果找不到 public 方法，尝试查找所有声明的方法（包括 private、protected、package-private）
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == argc) {
                log.debug("[TaskNodeExecutor] 找到匹配的 declared 方法: method={}, parameterTypes={}, modifiers={}", 
                    method, method.getParameterTypes(), java.lang.reflect.Modifier.toString(method.getModifiers()));
                // 如果是非 public 方法，需要设置可访问
                if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    method.setAccessible(true);
                    log.debug("[TaskNodeExecutor] 设置方法可访问: method={}", method);
                }
                return method;
            }
        }
        
        // 3. 如果还是找不到，列出所有可用的方法，便于调试
        log.error("[TaskNodeExecutor] 未找到方法: clazz={}, name={}, argc={}", clazz.getName(), name, argc);
        
        // 收集所有可用的方法名和参数数量
        java.util.Set<String> availableMethods = new java.util.LinkedHashSet<>();
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name)) {
                availableMethods.add(method.getName() + "/" + method.getParameterCount() + 
                    " (" + java.util.Arrays.toString(method.getParameterTypes()) + ")");
            }
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(name) && !availableMethods.contains(method.getName() + "/" + method.getParameterCount())) {
                availableMethods.add(method.getName() + "/" + method.getParameterCount() + 
                    " (" + java.util.Arrays.toString(method.getParameterTypes()) + ")");
            }
        }
        
        String errorMsg = "Method not found: " + clazz.getName() + "." + name + "/" + argc;
        if (!availableMethods.isEmpty()) {
            errorMsg += ". Available methods with name '" + name + "': " + String.join(", ", availableMethods);
        } else {
            // 如果连同名方法都没有，列出所有方法
            java.util.Set<String> allMethods = new java.util.LinkedHashSet<>();
            for (Method method : clazz.getMethods()) {
                allMethods.add(method.getName() + "/" + method.getParameterCount());
            }
            for (Method method : clazz.getDeclaredMethods()) {
                allMethods.add(method.getName() + "/" + method.getParameterCount());
            }
            errorMsg += ". Available methods in " + clazz.getName() + ": " + String.join(", ", allMethods);
        }
        
        throw new IllegalArgumentException(errorMsg);
    }

    /**
     * 转换参数类型
     * 将参数值转换为方法参数类型
     */
    private Object[] convertArgs(Class<?>[] parameterTypes, List<Object> args) {
        log.debug("[TaskNodeExecutor] 转换参数类型: parameterTypes={}, args={}", parameterTypes, args);
        Object[] converted = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object value = i < args.size() ? args.get(i) : null;
            Class<?> targetType = parameterTypes[i];
            Object convertedValue = convert(value, targetType);
            log.debug("[TaskNodeExecutor] 参数 [{}] 类型转换: value={}, valueType={}, targetType={}, convertedValue={}", 
                i, value, value != null ? value.getClass().getName() : "null", targetType.getName(), convertedValue);
            converted[i] = convertedValue;
        }
        log.debug("[TaskNodeExecutor] 参数类型转换完成: converted={}", converted);
        return converted;
    }

    /**
     * 类型转换
     * 将值转换为目标类型
     */
    private Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(String.valueOf(value));
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(String.valueOf(value));
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(String.valueOf(value));
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        return value;
    }
}


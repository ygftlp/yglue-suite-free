package org.yglue.flow.runtime.core.executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.expression.ExpressionEngines;
import org.yglue.flow.runtime.core.validator.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务节点执行器
 * <p>
 * 用于执行本地服务调用，通过反射调用 Spring Bean 方法。
 * 所有入参值统一通过 Groovy 脚本执行获取，确保参数赋值的统一化。
 * </p>
 * 
 * <p>节点配置格式：</p>
 * <pre>{@code
 * {
 *   "type": "service",
 *   "comp": {
 *     "bean": "bean名称（Service name）",
 *     "method": "方法名",
 *     "configJson": {
 *       "bean": "bean名称",
 *       "method": "方法名"
 *     }
 *   },
 *   "inputs": [
 *     {
 *       "name": "参数名",
 *       "valueType": "STRING",
 *       "typeName": "",
 *       "script": "ctx.request.path.projectKey"  // Groovy 脚本，用于获取参数值
 *     }
 *   ],
 *   "as": "result"  // 可选，将结果保存到上下文的 key
 * }
 * }</pre>
 * 
 * <p>脚本执行环境：</p>
 * <ul>
 *   <li>{@code ctx} - 流程上下文数据（Map），可通过 {@code ctx.request.path.xxx}、{@code ctx['key']} 等方式访问</li>
 * </ul>
 * 
 * @author yglue
 * @since 1.0
 */
public class ServiceNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ServiceNodeExecutor.class);

    /** Spring 应用上下文，用于获取 Bean 实例 */
    private final ApplicationContext applicationContext;
    
    /** JSON 对象映射器，用于配置对象的转换 */
    private final ObjectMapper objectMapper;
    
    /** 校验引擎，用于执行参数校验 */
    private final ValidatorEngine validatorEngine;

    /**
     * 构造函数
     * 
     * @param applicationContext Spring 应用上下文，不能为 null
     */
    public ServiceNodeExecutor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.objectMapper = new ObjectMapper();
        this.validatorEngine = new DefaultValidatorEngine();
    }

    /**
     * 执行服务节点
     * <p>
     * 执行流程：
     * <ol>
     *   <li>解析节点配置，获取 bean 名称和方法名</li>
     *   <li>从 Spring 容器获取 bean 实例和实际类型</li>
     *   <li>解析输入参数，统一通过脚本执行获取参数值</li>
     *   <li>查找匹配的方法（根据方法名和参数数量）</li>
     *   <li>转换参数类型并调用方法</li>
     *   <li>将结果保存到流程上下文（如果配置了 as 字段）</li>
     * </ol>
     * </p>
     * 
     * @param context 节点执行上下文，包含节点定义和流程上下文
     * @return 方法调用的返回值
     * @throws Exception 如果执行过程中出现错误
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object execute(NodeExecutionContext context) throws Exception {
        String nodeId = context.getNode().getId();
        log.info("[ServiceNodeExecutor] 开始执行任务节点: nodeId={}", nodeId);
        
        Map<String, Object> config = context.getNode().getConfig();
        log.debug("[ServiceNodeExecutor] 节点配置: nodeId={}, config={}", nodeId, config);
        
        // 获取组件配置
        Object compObj = config.get("comp");
        if (compObj == null) {
            log.error("[ServiceNodeExecutor] 任务节点缺少 'comp' 配置: nodeId={}", nodeId);
            throw new IllegalArgumentException("Task node must have 'comp' configuration");
        }
        log.debug("[ServiceNodeExecutor] 获取到组件配置: nodeId={}, compObj={}", nodeId, compObj);
        
        Map<String, Object> comp = compObj instanceof Map 
            ? (Map<String, Object>) compObj 
            : objectMapper.convertValue(compObj, Map.class);
        log.debug("[ServiceNodeExecutor] 组件配置已转换: nodeId={}, comp={}", nodeId, comp);
        
        // 获取 bean 名称和方法名
        String beanName = getBeanName(comp);
        String methodName = getMethodName(comp);
        log.info("[ServiceNodeExecutor] 解析组件信息: nodeId={}, beanName={}, methodName={}", nodeId, beanName, methodName);
        
        if (beanName == null || beanName.isBlank()) {
            log.error("[ServiceNodeExecutor] bean 名称为空: nodeId={}, comp={}", nodeId, comp);
            throw new IllegalArgumentException("Service node 'comp.bean' or 'comp.configJson.bean' is required");
        }
        if (methodName == null || methodName.isBlank()) {
            log.error("[ServiceNodeExecutor] 方法名为空: nodeId={}, comp={}", nodeId, comp);
            throw new IllegalArgumentException("Service node 'comp.method' or 'comp.configJson.method' is required");
        }
        
        // 从 Spring 容器中获取 bean 的实际类型
        log.debug("[ServiceNodeExecutor] 从 Spring 容器获取 bean: nodeId={}, beanName={}", nodeId, beanName);
        Object bean;
        Class<?> beanType;
        try {
            // 通过 BeanFactory 获取 bean 的实际类型（处理代理类的情况）
            // ApplicationContext 继承自 BeanFactory，所以可以直接使用
            beanType = applicationContext.getType(beanName);
            log.debug("[ServiceNodeExecutor] 从 BeanFactory 获取 bean 类型: nodeId={}, beanName={}, beanType={}", 
                nodeId, beanName, beanType != null ? beanType.getName() : "null");
            
            // 获取 bean 实例
            bean = applicationContext.getBean(beanName);
            log.info("[ServiceNodeExecutor] 成功获取 bean: nodeId={}, beanName={}, beanClass={}, beanType={}", 
                nodeId, beanName, 
                bean != null ? bean.getClass().getName() : "null",
                beanType != null ? beanType.getName() : "null");
        } catch (Exception e) {
            log.error("[ServiceNodeExecutor] 获取 bean 失败: nodeId={}, beanName={}, error={}", 
                nodeId, beanName, e.getMessage(), e);
            throw new IllegalArgumentException("Bean not found: " + beanName, e);
        }
        
        if (bean == null) {
            log.error("[ServiceNodeExecutor] bean 为 null: nodeId={}, beanName={}", nodeId, beanName);
            throw new IllegalArgumentException("Bean not found: " + beanName);
        }
        
        // 确定用于查找方法的类：优先使用 BeanFactory 获取的实际类型，否则使用 AopProxyUtils 处理代理类
        Class<?> targetClass;
        if (beanType != null) {
            targetClass = beanType;
            log.debug("[ServiceNodeExecutor] 使用 BeanFactory 获取的实际类型: nodeId={}, targetClass={}", 
                nodeId, targetClass.getName());
        } else {
            // 使用 AopProxyUtils 获取目标类（处理 CGLIB 和 JDK 动态代理）
            targetClass = AopProxyUtils.ultimateTargetClass(bean);
            log.debug("[ServiceNodeExecutor] 使用 AopProxyUtils 获取目标类: nodeId={}, targetClass={}, beanClass={}", 
                nodeId, targetClass.getName(), bean.getClass().getName());
        }
        
        // 解析输入参数 - 统一通过脚本执行获取参数值，并执行校验
        log.debug("[ServiceNodeExecutor] 开始解析输入参数: nodeId={}", nodeId);
        List<Object> arguments = new ArrayList<>();
        Object inputsConfig = config.get("inputs");
        if (inputsConfig instanceof List<?> inputs) {
            log.debug("[ServiceNodeExecutor] 输入参数配置数量: nodeId={}, count={}", nodeId, inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                Object inputObj = inputs.get(i);
                Map<String, Object> input = inputObj instanceof Map
                    ? (Map<String, Object>) inputObj
                    : objectMapper.convertValue(inputObj, Map.class);
                
                // 统一通过脚本执行获取参数值
                Object value = resolveInputValue(input, context.getContext(), i, nodeId);
                log.debug("[ServiceNodeExecutor] 参数 [{}] 解析结果: nodeId={}, value={}, valueType={}", 
                    i, nodeId, value, value != null ? value.getClass().getName() : "null");
                
                // 执行参数校验
                validateInput(value, input, context, i, nodeId, config);
                
                arguments.add(value);
            }
        } else {
            log.debug("[ServiceNodeExecutor] 无输入参数配置: nodeId={}", nodeId);
        }
        log.info("[ServiceNodeExecutor] 输入参数解析完成: nodeId={}, argumentCount={}", nodeId, arguments.size());
        
        // 查找并调用方法（使用实际类型而不是代理类）
        log.debug("[ServiceNodeExecutor] 查找方法: nodeId={}, targetClass={}, methodName={}, argumentCount={}", 
            nodeId, targetClass.getName(), methodName, arguments.size());
        Method method = resolveMethod(targetClass, methodName, arguments);
        log.info("[ServiceNodeExecutor] 找到方法: nodeId={}, method={}", nodeId, method);
        
        log.debug("[ServiceNodeExecutor] 转换参数类型: nodeId={}, parameterTypes={}", 
            nodeId, method.getParameterTypes());
        Object[] convertedArgs = convertArgs(method, arguments);
        log.debug("[ServiceNodeExecutor] 参数类型转换完成: nodeId={}, convertedArgs={}", nodeId, convertedArgs);
        
        log.info("[ServiceNodeExecutor] 开始调用方法: nodeId={}, beanName={}, method={}", 
            nodeId, beanName, method.getName());
        Object result;
        try {
            result = method.invoke(bean, convertedArgs);
            log.info("[ServiceNodeExecutor] 方法调用成功: nodeId={}, beanName={}, method={}, resultType={}", 
                nodeId, beanName, method.getName(), result != null ? result.getClass().getName() : "null");
        } catch (Exception e) {
            log.error("[ServiceNodeExecutor] 方法调用失败: nodeId={}, beanName={}, method={}, error={}", 
                nodeId, beanName, method.getName(), e.getMessage(), e);
            throw e;
        }
        
        // 将结果保存到上下文（如果有配置）
        Object alias = config.get("as");
        if (alias != null) {
            String aliasStr = String.valueOf(alias);
            log.debug("[ServiceNodeExecutor] 保存结果到上下文: nodeId={}, alias={}", nodeId, aliasStr);
            context.getContext().set(aliasStr, result);
        }
        
        log.info("[ServiceNodeExecutor] 任务节点执行完成: nodeId={}, beanName={}, method={}", 
            nodeId, beanName, methodName);
        return result;
    }

    /**
     * 获取方法名
     * <p>
     * 从组件配置的 configJson.method 字段获取方法名。
     * 支持多种配置格式：
     * <ul>
     *   <li>configJson 为字符串：解析 JSON 字符串</li>
     *   <li>configJson 为 Map：直接获取</li>
     *   <li>configJson 为其他类型：通过 ObjectMapper 转换</li>
     * </ul>
     * </p>
     * 
     * @param comp 组件配置 Map
     * @return 方法名，如果未找到则返回 null
     */
    @SuppressWarnings("unchecked")
    private String getMethodName(Map<String, Object> comp) {
        log.debug("[ServiceNodeExecutor] 开始获取方法名: comp={}", comp);
        
        Object configJsonObj = comp.get("configJson");
        if (configJsonObj == null) {
            log.warn("[ServiceNodeExecutor] configJson 为空，无法获取方法名");
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
                log.debug("[ServiceNodeExecutor] 从 configJson.method 获取方法名: methodName={}", methodName);
                return methodName;
            }
            
            log.warn("[ServiceNodeExecutor] configJson 中未找到 method 字段");
            return null;
        } catch (Exception e) {
            log.error("[ServiceNodeExecutor] 解析 configJson 失败: error={}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取 bean 名称
     * <p>
     * 按照以下优先级顺序查找 bean 名称：
     * <ol>
     *   <li>comp.bean（直接字段）</li>
     *   <li>configJson.bean（新字段名）</li>
     *   <li>configJson.serviceBean（向后兼容）</li>
     *   <li>configJson.flowApiBeanName（向后兼容，已废弃）</li>
     * </ol>
     * </p>
     * 
     * @param comp 组件配置 Map
     * @return bean 名称，如果未找到则返回 null
     */
    @SuppressWarnings("unchecked")
    private String getBeanName(Map<String, Object> comp) {
        log.debug("[ServiceNodeExecutor] 开始获取 bean 名称: comp={}", comp);
        
        // 优先级1：直接字段 comp.bean
        Object beanObj = comp.get("bean");
        if (beanObj != null && !String.valueOf(beanObj).isBlank()) {
            String beanName = String.valueOf(beanObj);
            log.debug("[ServiceNodeExecutor] 从 comp.bean 获取 bean 名称: beanName={}", beanName);
            return beanName;
        }
        
        // 优先级2-4：从 configJson 中获取
        Object configJsonObj = comp.get("configJson");
        if (configJsonObj == null) {
            log.warn("[ServiceNodeExecutor] configJson 为空，且 comp.bean 也为空，无法获取 bean 名称");
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
            
            // 优先级2：configJson.bean（新字段名）
            Object bean = configJson.get("bean");
            if (bean != null && !String.valueOf(bean).isBlank()) {
                String beanName = String.valueOf(bean);
                log.debug("[ServiceNodeExecutor] 从 configJson.bean 获取 bean 名称: beanName={}", beanName);
                return beanName;
            }
            
            // 优先级3：configJson.serviceBean（向后兼容）
            Object serviceBean = configJson.get("serviceBean");
            if (serviceBean != null && !String.valueOf(serviceBean).isBlank()) {
                String beanName = String.valueOf(serviceBean);
                log.debug("[ServiceNodeExecutor] 从 configJson.serviceBean 获取 bean 名称: beanName={}", beanName);
                return beanName;
            }
            
            // 优先级4：configJson.flowApiBeanName（向后兼容，已废弃）
            Object flowApiBeanName = configJson.get("flowApiBeanName");
            if (flowApiBeanName != null && !String.valueOf(flowApiBeanName).isBlank()) {
                String beanName = String.valueOf(flowApiBeanName);
                log.debug("[ServiceNodeExecutor] 从 configJson.flowApiBeanName 获取 bean 名称（已废弃）: beanName={}", beanName);
                return beanName;
            }
            
            log.warn("[ServiceNodeExecutor] configJson 中未找到 bean、serviceBean 或 flowApiBeanName 字段");
            return null;
        } catch (Exception e) {
            log.error("[ServiceNodeExecutor] 解析 configJson 失败: error={}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析方法
     * <p>
     * 根据方法名和参数数量查找匹配的方法。
     * </p>
     * 
     * <p>查找策略：</p>
     * <ol>
     *   <li>先查找 public 方法（包括继承的方法）</li>
     *   <li>如果找不到，再查找所有声明的方法（包括 private、protected、package-private）</li>
     *   <li>如果还是找不到，抛出异常并列出所有可用的方法名和参数数量，便于调试</li>
     * </ol>
     * 
     * @param clazz 目标类
     * @param name 方法名
     * @param argc 参数数量
     * @return 匹配的方法
     * @throws IllegalArgumentException 如果找不到匹配的方法
     */
    private Method resolveMethod(Class<?> clazz, String name, java.util.List<Object> args) {
        log.debug("[ServiceNodeExecutor] 查找方法: clazz={}, name={}, argc={}", clazz.getName(), name, args.size());
        java.util.List<Method> candidates = new java.util.ArrayList<>();
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) {
                candidates.add(m);
            }
        }
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                candidates.add(m);
            }
        }
        Method best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Method m : candidates) {
            Class<?>[] pts = m.getParameterTypes();
            boolean varargs = m.isVarArgs();
            if (!varargs && pts.length != args.size()) {
                continue;
            }
            if (varargs && args.size() < pts.length - 1) {
                continue;
            }
            int score = 0;
            boolean convertible = true;
            int fixedCount = varargs ? pts.length - 1 : pts.length;
            for (int i = 0; i < fixedCount; i++) {
                Class<?> pt = pts[i];
                Object val = i < args.size() ? args.get(i) : null;
                if (!canConvertTo(pt, val)) {
                    convertible = false;
                    break;
                }
                if (!(val != null && pt.isInstance(val))) {
                    score += 1;
                }
            }
            if (convertible && varargs) {
                Class<?> compType = pts[pts.length - 1].getComponentType();
                for (int j = fixedCount; j < args.size(); j++) {
                    Object val = args.get(j);
                    if (!canConvertTo(compType, val)) {
                        convertible = false;
                        break;
                    }
                    if (!(val != null && compType.isInstance(val))) {
                        score += 1;
                    }
                }
            }
            if (!convertible) {
                continue;
            }
            if (best == null || score < bestScore || (score == bestScore && java.lang.reflect.Modifier.isPublic(m.getModifiers()) && !java.lang.reflect.Modifier.isPublic(best.getModifiers()))) {
                best = m;
                bestScore = score;
            }
        }
        if (best != null) {
            if (!java.lang.reflect.Modifier.isPublic(best.getModifiers())) {
                best.setAccessible(true);
            }
            return best;
        }
        // 未找到方法，收集信息便于调试
        log.error("[ServiceNodeExecutor] 未找到方法: clazz={}, name={}, argc={}", clazz.getName(), name, args.size());
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
        String errorMsg = "Method not found: " + clazz.getName() + "." + name + "/" + args.size();
        if (!availableMethods.isEmpty()) {
            errorMsg += ". Available methods with name '" + name + "': " + String.join(", ", availableMethods);
        } else {
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
     * <p>
     * 将参数值列表转换为方法参数类型数组。
     * 如果参数值数量少于方法参数数量，缺失的参数会被设置为 null。
     * </p>
     * 
     * @param parameterTypes 方法参数类型数组
     * @param args 参数值列表
     * @return 转换后的参数数组
     */
    private Object[] convertArgs(Method method, List<Object> args) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        boolean varargs = method.isVarArgs();
        if (!varargs) {
            Object[] converted = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Object value = i < args.size() ? args.get(i) : null;
                converted[i] = convert(value, parameterTypes[i]);
            }
            return converted;
        } else {
            int fixed = parameterTypes.length - 1;
            Class<?> compType = parameterTypes[parameterTypes.length - 1].getComponentType();
            Object[] converted = new Object[parameterTypes.length];
            for (int i = 0; i < fixed; i++) {
                Object value = i < args.size() ? args.get(i) : null;
                converted[i] = convert(value, parameterTypes[i]);
            }
            int varCount = Math.max(0, args.size() - fixed);
            Object varArray = java.lang.reflect.Array.newInstance(compType, varCount);
            for (int j = 0; j < varCount; j++) {
                Object value = args.get(fixed + j);
                Object cv = convert(value, compType);
                java.lang.reflect.Array.set(varArray, j, cv);
            }
            converted[parameterTypes.length - 1] = varArray;
            return converted;
        }
    }

    private boolean canConvertTo(Class<?> targetType, Object value) {
        if (value == null) {
            return true;
        }
        if (targetType.isInstance(value)) {
            return true;
        }
        try {
            Object converted = convert(value, targetType);
            if (converted == null) {
                return true;
            }
            if (targetType.isPrimitive()) {
                return true;
            }
            return targetType.isInstance(converted) || converted.getClass().equals(targetType) || (targetType == String.class && converted instanceof String);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 类型转换
     * <p>
     * 将值转换为目标类型。支持以下类型的转换：
     * <ul>
     *   <li>基本类型：int、long、double、boolean</li>
     *   <li>包装类型：Integer、Long、Double、Boolean</li>
     *   <li>String 类型</li>
     *   <li>其他类型：如果值已经是目标类型的实例，直接返回</li>
     * </ul>
     * </p>
     * 
     * @param value 要转换的值
     * @param targetType 目标类型
     * @return 转换后的值，如果 value 为 null 则返回 null
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
        // BigDecimal / BigInteger
        if (targetType == java.math.BigDecimal.class) {
            return new java.math.BigDecimal(String.valueOf(value));
        }
        if (targetType == java.math.BigInteger.class) {
            return new java.math.BigInteger(String.valueOf(value));
        }
        // Java time (ISO-8601)
        if (targetType == java.time.LocalDate.class) {
            return java.time.LocalDate.parse(String.valueOf(value));
        }
        if (targetType == java.time.LocalDateTime.class) {
            return java.time.LocalDateTime.parse(String.valueOf(value));
        }
        // Enum by name
        if (targetType.isEnum()) {
            String name = String.valueOf(value);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object enumValue = java.lang.Enum.valueOf((Class<? extends java.lang.Enum>) targetType.asSubclass(java.lang.Enum.class), name);
            return enumValue;
        }
        // Map -> POJO via ObjectMapper
        if (value instanceof java.util.Map) {
            try {
                return objectMapper.convertValue(value, targetType);
            } catch (IllegalArgumentException ignore) {
                // fall through
            }
        }
        return value;
    }

    /**
     * 统一通过脚本执行解析输入参数值
     * <p>
     * 确保所有入参都通过脚本执行一次，实现参数赋值的统一化。
     * 服务节点的入参直接使用 script 字段存储 Groovy 脚本，统一执行脚本获取参数值。
     * </p>
     * 
     * <p>脚本执行环境：</p>
     * <ul>
     *   <li>{@code ctx} - 流程上下文数据（Map），包含完整的上下文数据，包括：
     *     <ul>
     *       <li>{@code ctx.request} - REST 请求参数对象（如果流程由 REST 请求触发）</li>
     *       <li>{@code ctx.request.path.xxx} - 路径变量</li>
     *       <li>{@code ctx.request.query.xxx} - 查询参数</li>
     *       <li>{@code ctx.request.body.xxx} - 请求体字段</li>
     *       <li>{@code ctx.request.headers.xxx} - 请求头</li>
     *       <li>{@code ctx['key']} - 其他流程上下文数据</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * @param input 输入参数配置 Map，应包含 script 字段
     * @param flowContext 流程上下文
     * @param index 参数索引，用于日志记录
     * @param nodeId 节点ID，用于日志记录
     * @return 解析后的参数值，如果 script 不存在或为空则返回 null
     * @throws RuntimeException 如果脚本执行失败
     */
    private Object resolveInputValue(Map<String, Object> input, 
                                     org.yglue.flow.runtime.FlowContext flowContext, 
                                     int index, 
                                     String nodeId) {
        // 获取 script 字段
        Object scriptObj = input.get("script");
        if (scriptObj == null || String.valueOf(scriptObj).trim().isEmpty()) {
            log.warn("[ServiceNodeExecutor] 参数 [{}] 没有 script 配置，返回 null: nodeId={}", index, nodeId);
            return null;
        }
        
        String script = String.valueOf(scriptObj).trim();
        
        log.debug("[ServiceNodeExecutor] 执行参数脚本 [{}]: nodeId={}, script={}", index, nodeId, script);
        
        // 统一通过 Groovy 脚本执行获取值
        try {
            // 创建 Groovy 绑定，注入流程上下文
            // ctx 包含完整的流程上下文数据，包括：
            // - request: REST 请求参数（request.path.xxx, request.query.xxx, request.body.xxx 等）
            // - 其他流程上下文数据
            Binding binding = new Binding();
            binding.setVariable("ctx", flowContext.data());
            
            // 执行脚本
            GroovyShell shell = new GroovyShell(binding);
            Object result = shell.evaluate(script);
            log.debug("[ServiceNodeExecutor] 脚本执行结果 [{}]: nodeId={}, result={}, resultType={}", 
                index, nodeId, result, result != null ? result.getClass().getName() : "null");
            return result;
        } catch (Exception e) {
            log.error("[ServiceNodeExecutor] 脚本执行失败 [{}]: nodeId={}, script={}, error={}", 
                index, nodeId, script, e.getMessage(), e);
            
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
            
            throw new RuntimeException("Failed to execute script for input parameter [" + index + "]: " 
                + errorMessage + helpfulHint + "\n脚本内容: " + script, e);
        }
    }
    
    /**
     * 校验输入参数
     * <p>
     * 根据输入参数配置中的 validators 列表执行校验。
     * 如果校验失败，根据 failPolicy 决定是否抛出异常。
     * </p>
     *
     * @param value 参数值
     * @param input 输入参数配置
     * @param context 节点执行上下文
     * @param index 参数索引
     * @param nodeId 节点ID
     * @param nodeConfig 节点配置
     * @throws RuntimeException 如果校验失败且 failPolicy 为 throw
     */
    private void validateInput(Object value, 
                               Map<String, Object> input, 
                               NodeExecutionContext context,
                               int index,
                               String nodeId,
                               Map<String, Object> nodeConfig) {
        // 获取输入参数名称和类型
        String inputName = getString(input, "name");
        if (inputName == null || inputName.isBlank()) {
            inputName = "参数[" + index + "]";
        }
        String valueType = getString(input, "valueType");
        if (valueType == null || valueType.isBlank()) {
            valueType = value != null ? inferValueType(value) : "STRING";
        }
        
        // 解析校验规则
        List<ValidationRule> rules = ValidationRuleParser.parseFromInput(input);
        
        // 如果是对象类型且有 typeName，尝试从 Bean Validation 注解中提取校验规则
        String typeName = getString(input, "typeName");
        if ("OBJECT".equals(valueType) && typeName != null && !typeName.isBlank() && rules.isEmpty()) {
            // 如果用户没有手动配置校验规则，尝试从 Bean Validation 注解中提取
            List<BeanValidationExtractor.ObjectFieldValidation> beanValidations = 
                BeanValidationExtractor.extractFieldValidations(typeName);
            
            // 如果提取到了 Bean Validation 规则，对对象字段进行校验
            if (!beanValidations.isEmpty() && value != null) {
                validateObjectFields(value, beanValidations, context, inputName, nodeId, input, nodeConfig);
            }
        }
        
        if (rules.isEmpty()) {
            return; // 没有校验规则，跳过
        }
        
        log.debug("[ServiceNodeExecutor] 开始校验参数 [{}]: nodeId={}, ruleCount={}", 
            index, nodeId, rules.size());
        
        // 创建校验上下文
        ValidationContext validationContext = new ValidationContext(
            context.getContext(),
            ExpressionEngines.getDefault(),
            inputName,
            valueType
        );
        
        // 执行校验
        List<ValidationResult> results = validatorEngine.validate(value, rules, validationContext);
        
        // 收集所有校验失败的结果
        List<ValidationException.ValidationError> validationErrors = new ArrayList<>();
        for (ValidationResult result : results) {
            if (!result.isPassed()) {
                String errorCode = mapValidatorTypeToErrorCode(result.getValidatorType());
                String message = result.getMessage() != null 
                    ? result.getMessage() 
                    : "参数 '" + inputName + "' 校验失败";
                validationErrors.add(new ValidationException.ValidationError(inputName, message, errorCode));
            }
        }
        
        // 如果有校验失败，根据 failPolicy 处理
        if (!validationErrors.isEmpty()) {
            String errorCode = validationErrors.size() == 1 
                ? validationErrors.get(0).getErrorCode() 
                : ValidationException.ERROR_CODE_VALIDATION_FAILED;
            String message = validationErrors.size() == 1
                ? validationErrors.get(0).getMessage()
                : "参数 '" + inputName + "' 校验失败，共 " + validationErrors.size() + " 个错误";
            
            log.warn("[ServiceNodeExecutor] 参数校验失败 [{}]: nodeId={}, errorCount={}, message={}", 
                index, nodeId, validationErrors.size(), message);
            
            // 获取 failPolicy（默认 throw）
            String failPolicy = getFailPolicy(input, nodeConfig);
            
            if ("throw".equals(failPolicy)) {
                throw new ValidationException(errorCode, message, inputName, validationErrors);
            } else if ("skip".equals(failPolicy)) {
                log.info("[ServiceNodeExecutor] 参数校验失败，跳过节点: nodeId={}, message={}", 
                    nodeId, message);
                throw new ValidationException(errorCode, "参数校验失败，跳过节点: " + message, inputName, validationErrors);
            }
            // failPolicy 为 "default" 时，继续执行（使用默认值）
        }
        
        log.debug("[ServiceNodeExecutor] 参数校验通过 [{}]: nodeId={}", index, nodeId);
    }
    
    /**
     * 获取校验失败策略
     * <p>
     * 优先级：input.failPolicy > node.validation.failPolicy > 默认 "throw"
     * </p>
     *
     * @param input 输入参数配置
     * @param nodeConfig 节点配置
     * @return 失败策略
     */
    @SuppressWarnings("unchecked")
    private String getFailPolicy(Map<String, Object> input, Map<String, Object> nodeConfig) {
        // 优先级1：input.failPolicy
        String inputFailPolicy = getString(input, "failPolicy");
        if (inputFailPolicy != null && !inputFailPolicy.isBlank()) {
            return inputFailPolicy;
        }
        
        // 优先级2：node.validation.failPolicy
        Object validationObj = nodeConfig.get("validation");
        if (validationObj != null) {
            Map<String, Object> validation;
            if (validationObj instanceof Map) {
                validation = (Map<String, Object>) validationObj;
            } else {
                try {
                    validation = objectMapper.convertValue(validationObj, Map.class);
                } catch (Exception e) {
                    validation = null;
                }
            }
            if (validation != null) {
                String nodeFailPolicy = getString(validation, "failPolicy");
                if (nodeFailPolicy != null && !nodeFailPolicy.isBlank()) {
                    return nodeFailPolicy;
                }
            }
        }
        
        // 默认策略
        return "throw";
    }
    
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }
    
    private String inferValueType(Object value) {
        if (value instanceof String) {
            return "STRING";
        } else if (value instanceof Number) {
            return "NUMBER";
        } else if (value instanceof Boolean) {
            return "BOOLEAN";
        } else if (value.getClass().isArray()) {
            return "ARRAY";
        } else {
            return "OBJECT";
        }
    }
    
    /**
     * 将校验器类型映射为错误码
     *
     * @param validatorType 校验器类型
     * @return 错误码
     */
    private String mapValidatorTypeToErrorCode(String validatorType) {
        if (validatorType == null) {
            return ValidationException.ERROR_CODE_VALIDATION_FAILED;
        }
        return switch (validatorType) {
            case "required" -> ValidationException.ERROR_CODE_REQUIRED_MISSING;
            case "type" -> ValidationException.ERROR_CODE_TYPE_MISMATCH;
            case "regex" -> ValidationException.ERROR_CODE_FORMAT_INVALID;
            case "range" -> ValidationException.ERROR_CODE_OUT_OF_RANGE;
            case "length" -> ValidationException.ERROR_CODE_LENGTH_INVALID;
            case "expression" -> ValidationException.ERROR_CODE_EXPRESSION_FAILED;
            default -> ValidationException.ERROR_CODE_VALIDATION_FAILED;
        };
    }
    
    /**
     * 校验对象字段
     *
     * @param object 对象实例
     * @param fieldValidations 字段校验规则列表
     * @param context 节点执行上下文
     * @param inputName 输入参数名称
     * @param nodeId 节点ID
     * @param input 输入参数配置（用于获取 failPolicy）
     * @param nodeConfig 节点配置（用于获取 failPolicy）
     */
    private void validateObjectFields(Object object,
                                      List<BeanValidationExtractor.ObjectFieldValidation> fieldValidations,
                                      NodeExecutionContext context,
                                      String inputName,
                                      String nodeId,
                                      Map<String, Object> input,
                                      Map<String, Object> nodeConfig) {
        List<ValidationException.ValidationError> validationErrors = new ArrayList<>();
        
        for (BeanValidationExtractor.ObjectFieldValidation fieldValidation : fieldValidations) {
            String fieldPath = fieldValidation.getFieldPath();
            List<ValidationRule> rules = fieldValidation.getRules();
            
            // 获取字段值
            Object fieldValue = getFieldValue(object, fieldPath);
            
            // 创建字段校验上下文
            ValidationContext fieldContext = new ValidationContext(
                context.getContext(),
                ExpressionEngines.getDefault(),
                inputName + "." + fieldPath,
                inferValueType(fieldValue)
            );
            
            // 执行字段校验
            List<ValidationResult> results = validatorEngine.validate(fieldValue, rules, fieldContext);
            
            // 收集校验失败的结果
            for (ValidationResult result : results) {
                if (!result.isPassed()) {
                    String errorCode = mapValidatorTypeToErrorCode(result.getValidatorType());
                    String message = result.getMessage() != null 
                        ? result.getMessage() 
                        : "字段 '" + fieldPath + "' 校验失败";
                    validationErrors.add(new ValidationException.ValidationError(
                        inputName + "." + fieldPath, message, errorCode));
                }
            }
        }
        
        // 如果有校验失败，根据 failPolicy 处理
        if (!validationErrors.isEmpty()) {
            String errorCode = validationErrors.size() == 1 
                ? validationErrors.get(0).getErrorCode() 
                : ValidationException.ERROR_CODE_VALIDATION_FAILED;
            String message = validationErrors.size() == 1
                ? validationErrors.get(0).getMessage()
                : "对象字段校验失败，共 " + validationErrors.size() + " 个错误";
            
            log.warn("[ServiceNodeExecutor] 对象字段校验失败: nodeId={}, inputName={}, errorCount={}", 
                nodeId, inputName, validationErrors.size());
            
            // 获取 failPolicy（默认 throw）
            String failPolicy = getFailPolicy(input, nodeConfig);
            
            if ("throw".equals(failPolicy)) {
                throw new ValidationException(errorCode, message, inputName, validationErrors);
            } else if ("skip".equals(failPolicy)) {
                log.info("[ServiceNodeExecutor] 对象字段校验失败，跳过节点: nodeId={}, message={}", 
                    nodeId, message);
                throw new ValidationException(errorCode, "对象字段校验失败，跳过节点: " + message, inputName, validationErrors);
            }
            // failPolicy 为 "default" 时，继续执行（使用默认值）
        }
    }
    
    /**
     * 获取对象字段值（支持嵌套字段和数组索引）
     *
     * @param object 对象实例
     * @param fieldPath 字段路径（如 "name"、"user.email"、"items[0].id"）
     * @return 字段值
     */
    private Object getFieldValue(Object object, String fieldPath) {
        if (object == null || fieldPath == null || fieldPath.isBlank()) {
            return null;
        }
        
        try {
            String[] parts = fieldPath.split("\\.");
            Object current = object;
            
            for (String part : parts) {
                if (current == null) {
                    return null;
                }
                
                // 处理数组索引（如 items[0]）
                if (part.contains("[") && part.contains("]")) {
                    int bracketIndex = part.indexOf('[');
                    String fieldName = part.substring(0, bracketIndex);
                    String indexStr = part.substring(bracketIndex + 1, part.indexOf(']'));
                    int index = Integer.parseInt(indexStr);
                    
                    // 获取字段
                    Object fieldObj = getFieldValueByReflection(current, fieldName);
                    if (fieldObj == null) {
                        return null;
                    }
                    
                    // 获取数组元素
                    if (fieldObj.getClass().isArray()) {
                        current = java.lang.reflect.Array.get(fieldObj, index);
                    } else if (fieldObj instanceof java.util.List) {
                        current = ((java.util.List<?>) fieldObj).get(index);
                    } else {
                        return null;
                    }
                } else {
                    current = getFieldValueByReflection(current, part);
                }
            }
            
            return current;
        } catch (Exception e) {
            log.debug("Failed to get field value for path {}: {}", fieldPath, e.getMessage());
            return null;
        }
    }
    
    /**
     * 通过反射或 Map 获取字段值
     * <p>
     * 支持两种方式：
     * 1. 如果对象是 Map，通过 key 获取值
     * 2. 否则通过反射获取字段值或调用 getter 方法
     * </p>
     *
     * @param object 对象实例
     * @param fieldName 字段名称
     * @return 字段值
     */
    private Object getFieldValueByReflection(Object object, String fieldName) {
        // 如果对象是 Map，直接通过 key 获取
        if (object instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) object;
            return map.get(fieldName);
        }
        
        // 否则通过反射获取字段值
        try {
            Class<?> clazz = object.getClass();
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(object);
        } catch (NoSuchFieldException e) {
            // 尝试通过 getter 方法获取
            try {
                String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                Method getter = object.getClass().getMethod(getterName);
                return getter.invoke(object);
            } catch (Exception ex) {
                log.debug("Failed to get field {} via getter: {}", fieldName, ex.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.debug("Failed to get field {}: {}", fieldName, e.getMessage());
            return null;
        }
    }
}

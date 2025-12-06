package org.yglue.flow.runtime.liteflow;

import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.FlowLoader;
import org.yglue.flow.runtime.events.EventBus;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 LiteFlow 的规则引擎实现
 * 将现有的 FlowDefinition 转换为 LiteFlow 规则并执行
 */
public class LiteFlowRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(LiteFlowRuleEngine.class);

    private final FlowLoader loader;
    private final FlowExecutor liteFlowExecutor;
    private final FlowDefinitionToLiteFlowConverter converter;  // 使用正确的转换器
    private final boolean reloadOnExecution;
    
    /**
     * 流程定义缓存
     * key: ruleId, value: FlowDefinition
     */
    private final Map<String, FlowDefinition> definitionCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * 
     * @param applicationContext Spring 应用上下文（已废弃，不再使用）
     * @param liteFlowExecutor LiteFlow 执行器
     * @param eventBus 事件总线（已废弃，不再使用）
     * @param reloadOnExecution 是否在执行时重新加载规则
     */
    public LiteFlowRuleEngine(ApplicationContext applicationContext,
                              FlowExecutor liteFlowExecutor,
                              EventBus eventBus,
                              boolean reloadOnExecution) {
        this.loader = new FlowLoader();
        this.liteFlowExecutor = liteFlowExecutor;
        this.converter = new FlowDefinitionToLiteFlowConverter();  // 使用正确的转换器
        this.reloadOnExecution = reloadOnExecution;
    }

    /**
     * 执行流程规则
     * 
     * @param ruleId 规则ID
     * @param input 输入参数
     * @return 执行结果
     * @throws IOException 如果加载规则失败
     */
    public FlowExecutionResult execute(String ruleId, Map<String, Object> input) throws IOException {
        FlowDefinition definition = loadDefinition(ruleId);
        
        // 通过XML格式注册规则
        registerRuleFromXml(ruleId);
        
        // 获取链名称
        String chainName = definition.getId();
        
        log.info("[LiteFlowRuleEngine] 执行流程规则: ruleId={}, chainName={}", ruleId, chainName);
        
        // 使用 LiteFlow 执行
        LiteflowResponse response = liteFlowExecutor.execute2Resp(chainName, input);
        
        // 转换执行结果
        return convertResponse(ruleId, response);
    }

    /**
     * 通过XML格式注册规则
     * 
     * @param ruleId 规则ID
     * @throws IOException 如果加载规则失败
     */
    public void registerRuleFromXml(String ruleId) throws IOException {
        FlowDefinition definition = loadDefinition(ruleId);
        
        // 生成XML规则内容
        String xmlRule = converter.generateXmlRule(definition);
        
        // 输出XML规则内容用于调试
        log.info("[LiteFlowRuleEngine] 生成的XML规则内容:\n{}", xmlRule);
        
        // TODO: 这里应该实现通过XML格式注册规则的逻辑
        // 由于LiteFlow的API限制，我们暂时还是使用原来的注册方式
        converter.convertAndRegister(definition);
    }

    /**
     * 加载流程定义
     * 根据 reloadOnExecution 配置决定是否使用缓存
     * 
     * @param ruleId 规则ID
     * @return 流程定义
     * @throws IOException 如果加载规则失败
     */
    private FlowDefinition loadDefinition(String ruleId) throws IOException {
        if (reloadOnExecution) {
            // 开发模式：每次都重新加载
            return loader.load(ruleId);
        } else {
            // 生产模式：使用缓存
            return definitionCache.computeIfAbsent(ruleId, id -> {
                try {
                    return loader.load(id);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load rule: " + id, e);
                }
            });
        }
    }

    /**
     * 转换 LiteFlow 响应为 FlowExecutionResult
     * 如果响应中包含异常，则抛出异常让上层捕获
     */
    private FlowExecutionResult convertResponse(String ruleId, LiteflowResponse response) {
        // 首先检查 LiteFlow 执行是否成功
        if (!response.isSuccess()) {
            // 获取异常信息
            Throwable cause = response.getCause();
            String message = response.getMessage();
            
            log.error("[LiteFlowRuleEngine] 流程执行失败: ruleId={}, message={}", ruleId, message, cause);
            
            // 抛出异常，让 FlowOrchestratedAspect 捕获并处理
            if (cause != null) {
                // ValidationException 需要直接抛出，因为 FlowOrchestratedAspect 中有专门的处理逻辑
                if (cause instanceof org.yglue.flow.runtime.core.validator.ValidationException) {
                    throw (org.yglue.flow.runtime.core.validator.ValidationException) cause;
                }
                // RuntimeException 直接抛出
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                // 其他异常包装后抛出
                throw new RuntimeException("流程执行失败: " + message, cause);
            } else {
                throw new RuntimeException("流程执行失败: " + (message != null ? message : "未知错误"));
            }
        }
        
        Map<String, Object> contextData = new java.util.HashMap<>();
        Object returnValue = null;
        
        // 从 LiteFlow 的 Context 中提取数据
        // LiteflowResponse 可能包含上下文数据，需要通过 getContext() 或其他方法获取
        try {
            // 尝试通过反射获取上下文
            Object context = getContextFromResponse(response);
            if (context != null) {
                contextData = extractDataFromContext(context);
                // 过滤掉LiteFlow内部对象
                contextData = (Map<String, Object>) filterNonSerializableObjects(contextData);
            }
        } catch (Exception e) {
            log.debug("[LiteFlowRuleEngine] 无法从响应中提取上下文数据", e);
        }
        
        // 获取执行结果
        // LiteflowResponse 通常有 getData() 或类似方法获取结果
        try {
            returnValue = getResultFromResponse(response);
            // 过滤掉LiteFlow内部对象
            returnValue = filterNonSerializableObjects(returnValue);
        } catch (Exception e) {
            log.debug("[LiteFlowRuleEngine] 无法从响应中提取结果", e);
        }
        
        return new FlowExecutionResult(
                ruleId,
                contextData != null ? contextData : Map.of(),
                returnValue
        );
    }
    
    /**
     * 从 LiteflowResponse 中获取上下文对象
     * LiteflowResponse 提供了 getFirstContextBean() 方法来获取第一个上下文对象
     */
    private Object getContextFromResponse(LiteflowResponse response) {
        try {
            // LiteflowResponse 有 getFirstContextBean() 方法
            return response.getFirstContextBean();
        } catch (Exception e) {
            log.debug("[LiteFlowRuleEngine] 无法获取上下文: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从上下文对象中提取数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDataFromContext(Object context) {
        if (context == null) {
            return new java.util.HashMap<>();
        }
        
        try {
            // 如果上下文本身就是 Map
            if (context instanceof Map) {
                // 创建一个新的Map，只包含可序列化的数据
                Map<String, Object> result = new java.util.HashMap<>();
                Map<?, ?> originalMap = (Map<?, ?>) context;
                for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
                    // 过滤掉LiteFlow内部对象
                    if (entry.getKey() instanceof String) {
                        String key = (String) entry.getKey();
                        Object value = entry.getValue();
                        // 检查value是否是LiteFlow内部对象
                        if (value != null && !value.getClass().getName().startsWith("com.yomahub.liteflow.")) {
                            result.put(key, value);
                        }
                    }
                }
                return result;
            }
            
            // 尝试调用 getData() 方法
            java.lang.reflect.Method getDataMethod = context.getClass().getMethod("getData");
            Object data = getDataMethod.invoke(context);
            if (data instanceof Map) {
                // 创建一个新的Map，只包含可序列化的数据
                Map<String, Object> result = new java.util.HashMap<>();
                Map<?, ?> originalMap = (Map<?, ?>) data;
                for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
                    // 过滤掉LiteFlow内部对象
                    if (entry.getKey() instanceof String) {
                        String key = (String) entry.getKey();
                        Object value = entry.getValue();
                        // 检查value是否是LiteFlow内部对象
                        if (value != null && !value.getClass().getName().startsWith("com.yomahub.liteflow.")) {
                            result.put(key, value);
                        }
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.debug("[LiteFlowRuleEngine] 从上下文提取数据失败: {}", e.getMessage());
        }
        
        return new java.util.HashMap<>();
    }
    
    /**
     * 从 LiteflowResponse 中获取执行结果
     * LiteflowResponse 通常通过上下文对象来获取结果数据
     */
    private Object getResultFromResponse(LiteflowResponse response) {
        try {
            // 从上下文对象中获取结果
            // 通常结果存储在上下文的某个字段中，或者通过 getFirstContextBean() 获取的上下文对象本身
            Object context = response.getFirstContextBean();
            if (context != null) {
                // 尝试从上下文中提取结果
                // 这里假设上下文对象有 getData() 方法或类似的方法
                Object result = extractResultFromContext(context);
                // 过滤掉LiteFlow内部对象
                return filterNonSerializableObjects(result);
            }
            return null;
        } catch (Exception e) {
            log.debug("[LiteFlowRuleEngine] 获取结果时出错: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从上下文对象中提取结果
     */
    private Object extractResultFromContext(Object context) {
        if (context == null) {
            return null;
        }
        
        try {
            // 尝试调用 getData() 方法获取结果
            java.lang.reflect.Method getDataMethod = context.getClass().getMethod("getData");
            Object data = getDataMethod.invoke(context);
            // 检查data是否是LiteFlow内部对象
            if (data != null && data.getClass().getName().startsWith("com.yomahub.liteflow.")) {
                return null;
            }
            return data;
        } catch (NoSuchMethodException e) {
            // 如果没有 getData() 方法，尝试其他常见方法
            try {
                java.lang.reflect.Method getResultMethod = context.getClass().getMethod("getResult");
                Object result = getResultMethod.invoke(context);
                // 检查result是否是LiteFlow内部对象
                if (result != null && result.getClass().getName().startsWith("com.yomahub.liteflow.")) {
                    return null;
                }
                return result;
            } catch (Exception ex) {
                // 如果都没有，检查context本身是否是LiteFlow内部对象
                if (context.getClass().getName().startsWith("com.yomahub.liteflow.")) {
                    return null;
                }
                return context;
            }
        } catch (Exception e) {
            log.debug("[LiteFlowRuleEngine] 从上下文提取结果失败: {}", e.getMessage());
            // 如果提取失败，检查context本身是否是LiteFlow内部对象
            if (context.getClass().getName().startsWith("com.yomahub.liteflow.")) {
                return null;
            }
            return context;
        }
    }
    
    /**
     * 过滤掉无法序列化的对象
     * 避免Jackson序列化LiteFlow内部对象时出现异常
     */
    private Object filterNonSerializableObjects(Object obj) {
        if (obj == null) {
            return null;
        }
        
        // 检查是否是LiteFlow内部对象
        String className = obj.getClass().getName();
        if (className.startsWith("com.yomahub.liteflow.")) {
            // 如果是LiteFlow内部对象，返回null或简单的字符串表示
            return "[LiteFlow Object: " + className + "]";
        }
        
        // 如果是Map类型，递归过滤其中的值
        if (obj instanceof java.util.Map) {
            java.util.Map<?, ?> originalMap = (java.util.Map<?, ?>) obj;
            java.util.Map<Object, Object> filteredMap = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> entry : originalMap.entrySet()) {
                filteredMap.put(entry.getKey(), filterNonSerializableObjects(entry.getValue()));
            }
            return filteredMap;
        }
        
        // 如果是Collection类型，递归过滤其中的元素
        if (obj instanceof java.util.Collection) {
            java.util.Collection<?> originalCollection = (java.util.Collection<?>) obj;
            java.util.Collection<Object> filteredCollection = new java.util.ArrayList<>();
            for (Object item : originalCollection) {
                filteredCollection.add(filterNonSerializableObjects(item));
            }
            return filteredCollection;
        }
        
        // 如果是数组类型，递归过滤其中的元素
        if (obj.getClass().isArray()) {
            Object[] originalArray = (Object[]) obj;
            Object[] filteredArray = new Object[originalArray.length];
            for (int i = 0; i < originalArray.length; i++) {
                filteredArray[i] = filterNonSerializableObjects(originalArray[i]);
            }
            return filteredArray;
        }
        
        // 其他情况直接返回原对象
        return obj;
    }
    
    /**
     * 清除指定规则的缓存
     * 用于手动刷新缓存
     * 
     * @param ruleId 规则ID
     */
    public void clearCache(String ruleId) {
        definitionCache.remove(ruleId);
        // 同时清除 LiteFlow 的规则缓存
        converter.unregister(ruleId);
    }

    /**
     * 清除所有规则的缓存
     */
    public void clearAllCache() {
        definitionCache.clear();
        converter.clearAll();
    }
}
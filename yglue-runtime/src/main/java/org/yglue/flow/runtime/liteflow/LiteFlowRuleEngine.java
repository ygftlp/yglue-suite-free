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
     */
    private FlowExecutionResult convertResponse(String ruleId, LiteflowResponse response) {
        Map<String, Object> contextData = new java.util.HashMap<>();
        Object returnValue = null;
        
        // 从 LiteFlow 的 Context 中提取数据
        // LiteflowResponse 可能包含上下文数据，需要通过 getContext() 或其他方法获取
        try {
            // 尝试通过反射获取上下文
            Object context = getContextFromResponse(response);
            if (context != null) {
                contextData = extractDataFromContext(context);
            }
        } catch (Exception e) {
            log.debug("[LiteFlowRuleEngine] 无法从响应中提取上下文数据", e);
        }
        
        // 获取执行结果
        // LiteflowResponse 通常有 getData() 或类似方法获取结果
        try {
            returnValue = getResultFromResponse(response);
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
                return extractResultFromContext(context);
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
            return data;
        } catch (NoSuchMethodException e) {
            // 如果没有 getData() 方法，尝试其他常见方法
            try {
                java.lang.reflect.Method getResultMethod = context.getClass().getMethod("getResult");
                return getResultMethod.invoke(context);
            } catch (Exception ex) {
                // 如果都没有，返回上下文对象本身
                return context;
            }
        } catch (Exception e) {
            log.debug("[LiteFlowRuleEngine] 从上下文提取结果失败: {}", e.getMessage());
            // 如果提取失败，返回上下文对象本身
            return context;
        }
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
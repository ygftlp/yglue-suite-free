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
    private final FlowDefinitionToLiteFlowConverter converter;
    private final boolean reloadOnExecution;
    
    /**
     * 流程定义缓存
     * key: ruleId, value: FlowDefinition
     */
    private final Map<String, FlowDefinition> definitionCache = new ConcurrentHashMap<>();

    public LiteFlowRuleEngine(ApplicationContext applicationContext,
                              FlowExecutor liteFlowExecutor,
                              EventBus eventBus,
                              boolean reloadOnExecution) {
        this.loader = new FlowLoader();
        this.liteFlowExecutor = liteFlowExecutor;
        this.converter = new FlowDefinitionToLiteFlowConverter(applicationContext);
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
        
        // 将 FlowDefinition 转换为 LiteFlow 规则
        String chainName = converter.convertAndRegister(definition);
        
        log.info("[LiteFlowRuleEngine] 执行流程规则: ruleId={}, chainName={}", ruleId, chainName);
        
        // 使用 LiteFlow 执行
        LiteflowResponse response = liteFlowExecutor.execute2Resp(chainName, input);
        
        // 转换执行结果
        return convertResponse(ruleId, response);
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
        Map<String, Object> contextData = null;
        Object returnValue = null;
        
        if (response.getContextBean() != null) {
            // 从 LiteFlow 的 Context 中提取数据
            // 这里需要根据实际的 LiteFlow Context 实现来调整
            contextData = Map.of(); // TODO: 从 response.getContextBean() 中提取数据
        }
        
        return new FlowExecutionResult(
                ruleId,
                contextData != null ? contextData : Map.of(),
                returnValue
        );
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



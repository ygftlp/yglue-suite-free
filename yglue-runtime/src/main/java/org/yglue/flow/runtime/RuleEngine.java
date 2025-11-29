package org.yglue.flow.runtime;

import com.yomahub.liteflow.core.FlowExecutor;
import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.ExecutionInterceptor;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.FlowLoader;
import org.yglue.flow.runtime.events.EventBus;
import org.yglue.flow.runtime.interceptors.LoggingInterceptor;
import org.yglue.flow.runtime.liteflow.LiteFlowRuleEngine;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则引擎
 * 负责加载和执行流程规则，基于 LiteFlow 框架执行流程编排
 */
public class RuleEngine {

    private final FlowLoader loader;
    private final LiteFlowRuleEngine liteFlowEngine;
    private final boolean reloadOnExecution;
    
    /**
     * 流程定义缓存
     * key: ruleId, value: FlowDefinition
     */
    private final Map<String, FlowDefinition> definitionCache = new ConcurrentHashMap<>();

    public RuleEngine(ApplicationContext applicationContext) {
        this(applicationContext, List.of(new LoggingInterceptor()), EventBus.noop(), false);
    }

    public RuleEngine(ApplicationContext applicationContext,
                      List<ExecutionInterceptor> interceptors,
                      EventBus eventBus) {
        this(applicationContext, interceptors, eventBus, false);
    }

    /**
     * 创建规则引擎
     * 
     * @param applicationContext Spring 应用上下文
     * @param interceptors 执行拦截器列表（已废弃，LiteFlow 使用自身拦截器机制）
     * @param eventBus 事件总线
     * @param reloadOnExecution 是否每次执行时重新加载规则
     *                          true: 每次执行都从文件重新加载（适合开发环境）
     *                          false: 使用缓存（适合生产环境，性能更好）
     */
    public RuleEngine(ApplicationContext applicationContext,
                      List<ExecutionInterceptor> interceptors,
                      EventBus eventBus,
                      boolean reloadOnExecution) {
        this.reloadOnExecution = reloadOnExecution;
        this.loader = new FlowLoader();
        
        // 使用 LiteFlow 引擎
        FlowExecutor liteFlowExecutor = applicationContext.getBean(FlowExecutor.class);
        this.liteFlowEngine = new LiteFlowRuleEngine(
                applicationContext,
                liteFlowExecutor,
                eventBus,
                reloadOnExecution
        );
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
        return liteFlowEngine.execute(ruleId, input);
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
     * 清除指定规则的缓存
     * 用于手动刷新缓存
     * 
     * @param ruleId 规则ID
     */
    public void clearCache(String ruleId) {
        definitionCache.remove(ruleId);
        liteFlowEngine.clearCache(ruleId);
    }

    /**
     * 清除所有规则的缓存
     */
    public void clearAllCache() {
        definitionCache.clear();
        liteFlowEngine.clearAllCache();
    }
}

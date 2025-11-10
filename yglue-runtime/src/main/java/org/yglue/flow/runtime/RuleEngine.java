package org.yglue.flow.runtime;

import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.ExecutionInterceptor;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.FlowLoader;
import org.yglue.flow.runtime.core.executors.BranchNodeExecutor;
import org.yglue.flow.runtime.core.executors.CallNodeExecutor;
import org.yglue.flow.runtime.core.executors.DelayNodeExecutor;
import org.yglue.flow.runtime.core.executors.IfNodeExecutor;
import org.yglue.flow.runtime.core.executors.LogNodeExecutor;
import org.yglue.flow.runtime.core.executors.RestNodeExecutor;
import org.yglue.flow.runtime.core.executors.SetNodeExecutor;
import org.yglue.flow.runtime.core.executors.TaskNodeExecutor;
import org.yglue.flow.runtime.core.executors.TransformerNodeExecutor;
import org.yglue.flow.runtime.events.EventBus;
import org.yglue.flow.runtime.interceptors.LoggingInterceptor;
import org.yglue.flow.runtime.rest.BeanRestInvocationStrategy;
import org.yglue.flow.runtime.rest.HttpRestInvocationStrategy;
import org.yglue.flow.runtime.rest.RestInvocationRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则引擎
 * 负责加载和执行流程规则
 */
public class RuleEngine {

    private final FlowLoader loader;
    private final FlowExecutor executor;
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
     * @param interceptors 执行拦截器列表
     * @param eventBus 事件总线
     * @param reloadOnExecution 是否每次执行时重新加载规则
     *                          true: 每次执行都从文件重新加载（适合开发环境）
     *                          false: 使用缓存（适合生产环境，性能更好）
     */
    public RuleEngine(ApplicationContext applicationContext,
                      List<ExecutionInterceptor> interceptors,
                      EventBus eventBus,
                      boolean reloadOnExecution) {
        this.loader = new FlowLoader();
        this.reloadOnExecution = reloadOnExecution;
        
        NodeExecutorRegistry registry = new NodeExecutorRegistry()
                .register("log", new LogNodeExecutor())
                .register("delay", new DelayNodeExecutor())
                .register("set", new SetNodeExecutor())
                .register("if", new IfNodeExecutor())
                .register("branch", new BranchNodeExecutor())
                .register("call", new CallNodeExecutor(applicationContext))
                .register("transformer", new TransformerNodeExecutor())
                .register("task", new TaskNodeExecutor(applicationContext));

        RestInvocationRegistry restRegistry = new RestInvocationRegistry()
                .register(new BeanRestInvocationStrategy())
                .register(new HttpRestInvocationStrategy());

        registry.register("rest", new RestNodeExecutor(restRegistry, applicationContext));

        this.executor = new FlowExecutor(registry,
                interceptors == null ? List.of(new LoggingInterceptor()) : new ArrayList<>(interceptors),
                eventBus);
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
        return executor.execute(definition, input);
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
    }

    /**
     * 清除所有规则的缓存
     */
    public void clearAllCache() {
        definitionCache.clear();
    }
}

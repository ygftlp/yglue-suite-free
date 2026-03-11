package org.yglue.flow.runtime;

import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.FlowLoader;
import org.yglue.flow.runtime.core.engine.GraphExecutor;
import org.yglue.flow.runtime.core.engine.NodeInterceptor;
import org.yglue.flow.runtime.core.engine.interceptors.ParamResolveInterceptor;
import org.yglue.flow.runtime.core.engine.interceptors.TransactionInterceptor;
import org.yglue.flow.runtime.core.executors.BranchNodeExecutor;
import org.yglue.flow.runtime.core.executors.CallNodeExecutor;
import org.yglue.flow.runtime.core.executors.DelayNodeExecutor;
import org.yglue.flow.runtime.core.executors.GroupNodeExecutor;
import org.yglue.flow.runtime.core.executors.IfNodeExecutor;
import org.yglue.flow.runtime.core.executors.LogNodeExecutor;
import org.yglue.flow.runtime.core.executors.RestNodeExecutor;
import org.yglue.flow.runtime.core.executors.ServiceNodeExecutor;
import org.yglue.flow.runtime.core.executors.SetNodeExecutor;
import org.yglue.flow.runtime.core.executors.TransformerNodeExecutor;
import org.yglue.flow.runtime.events.EventBus;
import org.yglue.flow.runtime.rest.BeanRestInvocationStrategy;
import org.yglue.flow.runtime.rest.HttpRestInvocationStrategy;
import org.yglue.flow.runtime.rest.RestInvocationRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RuleEngine {

    private final FlowLoader loader;
    private final GraphExecutor graphExecutor;
    private final EventBus eventBus;
    private final boolean reloadOnExecution;
    private final Map<String, FlowDefinition> definitionCache = new ConcurrentHashMap<>();

    public RuleEngine(ApplicationContext applicationContext) {
        this(applicationContext, null, EventBus.noop(), false);
    }

    public RuleEngine(ApplicationContext applicationContext, GraphExecutor graphExecutor, EventBus eventBus) {
        this(applicationContext, graphExecutor, eventBus, false);
    }

    public RuleEngine(ApplicationContext applicationContext,
            GraphExecutor graphExecutor,
            EventBus eventBus,
            boolean reloadOnExecution) {
        this.loader = new FlowLoader();
        this.eventBus = eventBus != null ? eventBus : EventBus.noop();
        this.reloadOnExecution = reloadOnExecution;
        this.graphExecutor = graphExecutor != null ? graphExecutor : buildDefaultGraphExecutor(applicationContext);
    }

    public FlowExecutionResult execute(String ruleId, Map<String, Object> input) throws IOException {
        Objects.requireNonNull(ruleId, "ruleId must not be null");

        Map<String, Object> safeInput = input == null ? Map.of() : input;
        eventBus.publish(Map.of("type", "flow.execute.start", "ruleId", ruleId));

        try {
            FlowDefinition definition = loadDefinition(ruleId);
            FlowExecutionResult result = graphExecutor.execute(definition, safeInput);
            eventBus.publish(Map.of("type", "flow.execute.success", "ruleId", ruleId));
            return result;
        } catch (IOException ex) {
            eventBus.publish(Map.of("type", "flow.execute.error", "ruleId", ruleId, "error", ex.getMessage()));
            throw ex;
        } catch (RuntimeException ex) {
            eventBus.publish(Map.of("type", "flow.execute.error", "ruleId", ruleId, "error", ex.getMessage()));
            throw ex;
        }
    }

    public void clearCache(String ruleId) {
        if (ruleId != null) {
            definitionCache.remove(ruleId);
        }
    }

    public void clearAllCache() {
        definitionCache.clear();
    }

    private FlowDefinition loadDefinition(String ruleId) throws IOException {
        if (reloadOnExecution) {
            return loader.load(ruleId);
        }

        try {
            return definitionCache.computeIfAbsent(ruleId, id -> {
                try {
                    return loader.load(id);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load rule: " + id, e);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }

    private GraphExecutor buildDefaultGraphExecutor(ApplicationContext applicationContext) {
        RestInvocationRegistry restInvocationRegistry = new RestInvocationRegistry()
                .register(new BeanRestInvocationStrategy())
                .register(new HttpRestInvocationStrategy());

        NodeExecutorRegistry registry = new NodeExecutorRegistry()
                .register("log", new LogNodeExecutor())
                .register("delay", new DelayNodeExecutor())
                .register("set", new SetNodeExecutor())
                .register("if", new IfNodeExecutor())
                .register("branch", new BranchNodeExecutor())
                .register("call", new CallNodeExecutor(applicationContext))
                .register("service", new ServiceNodeExecutor(applicationContext))
                .register("group", new GroupNodeExecutor())
                .register("rest", new RestNodeExecutor(restInvocationRegistry, applicationContext))
                .register("transformer", new TransformerNodeExecutor());

        List<NodeInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new ParamResolveInterceptor());
        interceptors.add(new TransactionInterceptor(resolveTransactionManager(applicationContext)));

        return new GraphExecutor(registry, interceptors);
    }

    private PlatformTransactionManager resolveTransactionManager(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(PlatformTransactionManager.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}

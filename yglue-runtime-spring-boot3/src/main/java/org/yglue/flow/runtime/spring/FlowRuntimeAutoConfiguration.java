package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.yglue.flow.runtime.RuleEngine;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.engine.GraphExecutor;
import org.yglue.flow.runtime.core.engine.NodeInterceptor;
import org.yglue.flow.runtime.core.engine.interceptors.ParamResolveInterceptor;
import org.yglue.flow.runtime.core.engine.interceptors.TransactionInterceptor;
import org.yglue.flow.runtime.core.executors.BranchNodeExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.yglue.flow.runtime.core.executors.CallNodeExecutor;
import org.yglue.flow.runtime.core.executors.DelayNodeExecutor;
import org.yglue.flow.runtime.core.executors.IfNodeExecutor;
import org.yglue.flow.runtime.core.executors.LogNodeExecutor;
import org.yglue.flow.runtime.core.executors.RestNodeExecutor;
import org.yglue.flow.runtime.core.executors.RequestScopedServiceNodeExecutor;
import org.yglue.flow.runtime.core.executors.ServiceNodeExecutor;
import org.yglue.flow.runtime.core.executors.SetNodeExecutor;
import org.yglue.flow.runtime.core.executors.TransformerNodeExecutor;
import org.yglue.flow.runtime.core.executors.GroupNodeExecutor;
import org.yglue.flow.runtime.events.EventBus;
import org.yglue.flow.runtime.interceptors.LoggingInterceptor;
import org.yglue.flow.runtime.rest.BeanRestInvocationStrategy;
import org.yglue.flow.runtime.rest.HttpRestInvocationStrategy;
import org.yglue.flow.runtime.rest.RestInvocationRegistry;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HandlerInterceptor.class)
@EnableConfigurationProperties(FlowRuntimeProperties.class)
@ConditionalOnProperty(prefix = "yglue.runtime.flow", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableAspectJAutoProxy
@ComponentScan(basePackages = { "org.yglue.flow.runtime" })
public class FlowRuntimeAutoConfiguration implements WebMvcConfigurer {

    private final FlowRuntimeProperties properties;
    private final ApplicationContext applicationContext;

    public FlowRuntimeAutoConfiguration(FlowRuntimeProperties properties,
            ApplicationContext applicationContext) {
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Order(2)
    @Bean
    @ConditionalOnMissingBean
    public RestEntryPointRegistry restEntryPointRegistry() {
        return RestEntryPointRegistry.loadDefault();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestSchemaValidator requestSchemaValidator(ObjectMapper objectMapper) {
        return new RequestSchemaValidator(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "traceInboundInterceptor")
    public InboundRequestInterceptor traceInboundInterceptor() {
        return new TraceInboundInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "authInboundInterceptor")
    public InboundRequestInterceptor authInboundInterceptor() {
        return new AuthInboundInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "multipartInboundInterceptor")
    public InboundRequestInterceptor multipartInboundInterceptor() {
        return new MultipartInboundInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "schemaNormalizeInboundInterceptor")
    public InboundRequestInterceptor schemaNormalizeInboundInterceptor(RequestSchemaValidator requestSchemaValidator) {
        return new SchemaNormalizeInboundInterceptor(requestSchemaValidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public InboundInterceptorChain inboundInterceptorChain(ObjectMapper objectMapper,
            List<InboundRequestInterceptor> inboundInterceptors) {
        return new InboundInterceptorChain(objectMapper, inboundInterceptors);
    }

    @Order(3)
    @Bean
    @ConditionalOnMissingBean
    public FlowDispatchInterceptor flowDispatchInterceptor(RuleEngine ruleEngine,
            ObjectMapper objectMapper,
            RestEntryPointRegistry entryPointRegistry,
            InboundInterceptorChain inboundInterceptorChain) {
        return new FlowDispatchInterceptor(ruleEngine, objectMapper, entryPointRegistry, inboundInterceptorChain);
    }

    @Bean
    @ConditionalOnMissingBean
    public ParamResolveInterceptor paramResolveInterceptor(ApplicationContext applicationContext) {
        return new ParamResolveInterceptor(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionInterceptor transactionInterceptor(
            @Autowired(required = false) PlatformTransactionManager txManager) {
        return new TransactionInterceptor(txManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleEngine ruleEngine(ApplicationContext applicationContext,
            List<NodeInterceptor> interceptors,
            EventBus eventBus,
            NodeExecutorRegistry nodeExecutorRegistry) {
        boolean reloadOnExecution = this.properties.isReloadOnExecution();
        GraphExecutor graphExecutor = new GraphExecutor(nodeExecutorRegistry, interceptors);
        return new RuleEngine(applicationContext, graphExecutor, eventBus, reloadOnExecution);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventBus eventBus() {
        return EventBus.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    public NodeInterceptor loggingExecutionInterceptor() {
        // Here we could wrap the old loggingInterceptor if needed, or simply return a
        // generic NodeInterceptor
        // For now, we drop the generic ExecutionInterceptor usage in favor of
        // NodeInterceptor.
        return new NodeInterceptor() {
            @Override
            public Object intercept(org.yglue.flow.runtime.core.engine.FlowContext context,
                    org.yglue.flow.runtime.core.definition.NodeDefinition node,
                    org.yglue.flow.runtime.core.engine.NodeInterceptorChain chain) throws Exception {
                // simple log wrapper
                return chain.proceed();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public NodeExecutorRegistry nodeExecutorRegistry(ApplicationContext applicationContext) {
        ServiceNodeExecutor serviceExecutor = new ServiceNodeExecutor(applicationContext);
        NodeExecutorRegistry registry = new NodeExecutorRegistry()
                .register("log", new LogNodeExecutor())
                .register("delay", new DelayNodeExecutor())
                .register("set", new SetNodeExecutor())
                .register("if", new IfNodeExecutor())
                .register("branch", new BranchNodeExecutor(applicationContext))
                .register("call", new CallNodeExecutor(applicationContext))
                .register("transformer", new TransformerNodeExecutor())
                .register("service", new RequestScopedServiceNodeExecutor(serviceExecutor))
                .register("serviceGroup", new GroupNodeExecutor());

        RestInvocationRegistry restRegistry = new RestInvocationRegistry()
                .register(new BeanRestInvocationStrategy())
                .register(new HttpRestInvocationStrategy());
        registry.register("rest", new RestNodeExecutor(restRegistry, applicationContext));
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowOrchestratedAspect flowOrchestratedAspect(RuleEngine ruleEngine,
            ObjectMapper objectMapper,
            RestEntryPointRegistry entryPointRegistry,
            InboundInterceptorChain inboundInterceptorChain) {
        return new FlowOrchestratedAspect(ruleEngine, objectMapper, entryPointRegistry, inboundInterceptorChain);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(applicationContext.getBean(FlowDispatchInterceptor.class));
    }
}

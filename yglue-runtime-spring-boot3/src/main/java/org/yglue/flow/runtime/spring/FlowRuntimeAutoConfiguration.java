package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import com.yomahub.liteflow.slot.DefaultContext;
import com.yomahub.liteflow.spi.spring.SpringAware;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.yglue.flow.runtime.RuleEngine;
import org.yglue.flow.runtime.core.ExecutionInterceptor;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.executors.*;
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
public class FlowRuntimeAutoConfiguration implements WebMvcConfigurer {

    private final FlowRuntimeProperties properties;
    private final ApplicationContext applicationContext;

    public FlowRuntimeAutoConfiguration(FlowRuntimeProperties properties,
                                        ApplicationContext applicationContext) {
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringAware springAware() {
        SpringAware springAware = new SpringAware();
        // 确保SpringAware正确初始化
        springAware.setApplicationContext(applicationContext);
        return springAware;
    }
    
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
    @ConditionalOnMissingBean
    public FlowDispatchInterceptor flowDispatchInterceptor(RuleEngine ruleEngine,
                                                           ObjectMapper objectMapper,
                                                           RestEntryPointRegistry entryPointRegistry,
                                                           RequestSchemaValidator requestSchemaValidator) {
        return new FlowDispatchInterceptor(ruleEngine, objectMapper, entryPointRegistry, requestSchemaValidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleEngine ruleEngine(ApplicationContext applicationContext,
                                 List<ExecutionInterceptor> interceptors,
                                 EventBus eventBus) {
        // 从配置中获取 reloadOnExecution 设置
        boolean reloadOnExecution = this.properties.isReloadOnExecution();
        return new RuleEngine(applicationContext, interceptors, eventBus, reloadOnExecution);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventBus eventBus() {
        return EventBus.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowExecutor flowExecutor() {
        // 直接从ApplicationContext获取FlowExecutor Bean
        try {
            return applicationContext.getBean(FlowExecutor.class);
        } catch (Exception e) {
            // 如果获取失败，创建一个简单的实现
            return new FlowExecutor() {
                public LiteflowResponse execute2Resp(String chainId, Object... params) {
                    return new LiteflowResponse();
                }
                
                public <T> T getFirstContextBean() {
                    return (T) new DefaultContext();
                }
            };
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionInterceptor loggingExecutionInterceptor() {
        return new LoggingInterceptor();
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
                .register("branch", new BranchNodeExecutor())
                .register("call", new CallNodeExecutor(applicationContext))
                .register("transformer", new TransformerNodeExecutor())
                .register("service", serviceExecutor);
        
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
                                                      RequestSchemaValidator requestSchemaValidator,
                                                      RestEntryPointRegistry entryPointRegistry) {
        return new FlowOrchestratedAspect(ruleEngine, objectMapper, requestSchemaValidator, entryPointRegistry);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(applicationContext.getBean(FlowDispatchInterceptor.class));
    }
}

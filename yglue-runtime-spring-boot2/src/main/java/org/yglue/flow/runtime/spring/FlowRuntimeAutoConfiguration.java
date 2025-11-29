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
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.yglue.flow.runtime.RuleEngine;
import org.yglue.flow.runtime.core.ExecutionInterceptor;
import org.yglue.flow.runtime.events.EventBus;
import org.yglue.flow.runtime.interceptors.LoggingInterceptor;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HandlerInterceptor.class)
@EnableConfigurationProperties(FlowRuntimeProperties.class)
@ConditionalOnProperty(prefix = "yglue.runtime.flow", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    public org.yglue.flow.runtime.core.NodeExecutorRegistry nodeExecutorRegistry(ApplicationContext applicationContext) {
        org.yglue.flow.runtime.core.executors.ServiceNodeExecutor serviceExecutor = new org.yglue.flow.runtime.core.executors.ServiceNodeExecutor(applicationContext);
        org.yglue.flow.runtime.core.NodeExecutorRegistry registry = new org.yglue.flow.runtime.core.NodeExecutorRegistry()
                .register("log", new org.yglue.flow.runtime.core.executors.LogNodeExecutor())
                .register("delay", new org.yglue.flow.runtime.core.executors.DelayNodeExecutor())
                .register("set", new org.yglue.flow.runtime.core.executors.SetNodeExecutor())
                .register("if", new org.yglue.flow.runtime.core.executors.IfNodeExecutor())
                .register("branch", new org.yglue.flow.runtime.core.executors.BranchNodeExecutor())
                .register("call", new org.yglue.flow.runtime.core.executors.CallNodeExecutor(applicationContext))
                .register("transformer", new org.yglue.flow.runtime.core.executors.TransformerNodeExecutor())
                .register("service", serviceExecutor);
        org.yglue.flow.runtime.rest.RestInvocationRegistry restRegistry = new org.yglue.flow.runtime.rest.RestInvocationRegistry()
                .register(new org.yglue.flow.runtime.rest.BeanRestInvocationStrategy())
                .register(new org.yglue.flow.runtime.rest.HttpRestInvocationStrategy());
        registry.register("rest", new org.yglue.flow.runtime.core.executors.RestNodeExecutor(restRegistry, applicationContext));
        return registry;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(applicationContext.getBean(FlowDispatchInterceptor.class));
    }
}

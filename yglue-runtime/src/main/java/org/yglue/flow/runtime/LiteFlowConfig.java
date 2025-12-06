package org.yglue.flow.runtime;

import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.property.LiteflowConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class LiteFlowConfig {

    @Bean
    public FlowExecutor flowExecutor() {
        // 不设置规则源，让LiteFlow使用默认配置
        // 我们的规则是通过LiteFlowRuleEngine动态注册的
        LiteflowConfig config = new LiteflowConfig();
        config.setEnable(true);
        // config.setParseMode(com.yomahub.liteflow.enums.ParseModeEnum.PARSE_ALL_ON_FIRST_EXEC);
        
        // 使用带参数的构造函数来避免LiteflowConfigGetter.get()返回null的问题
        FlowExecutor executor = new FlowExecutor(config);
        return executor;
    }
}
package org.yglue.flow.runtime;

import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.property.LiteflowConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class LiteFlowConfig {

    @Bean
    public FlowExecutor flowExecutor() {
        LiteflowConfig config = new LiteflowConfig();
        config.setRuleSource("config/flow.el.xml");
        config.setEnable(true);

        FlowExecutor executor = new FlowExecutor();
        executor.setLiteflowConfig(config);
        return executor;
    }
}

package org.yglue.flow.runtime.spring;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Flow 运行时配置属性
 */
@Data
@ConfigurationProperties(prefix = "yglue.runtime.flow")
public class FlowRuntimeProperties {

    /**
     * 是否启用 Flow 运行时
     * 默认：true
     */
    private boolean enabled = true;

    /**
     * 是否每次执行时重新加载流程规则
     * true: 每次执行都从文件重新加载规则（适合开发环境，规则修改后立即生效）
     * false: 使用缓存，首次加载后缓存规则（适合生产环境，性能更好）
     * 默认：false
     */
    private boolean reloadOnExecution = false;

    private String ruleSource = "ygflow/rules/*.json";


}

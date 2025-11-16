package org.yglue.flow.runtime.core.param.resolvers;

import org.yglue.flow.annotations.FlowResolver;

/**
 * 内置上下文解析器元数据。
 */
@FlowResolver(
        value = "CONTEXT",
        name = "流程上下文",
        description = "从流程上下文 ctx 或变量池中读取数据",
        category = "上下文",
        builtin = true
)
public final class ContextParamResolverMetadata {
    private ContextParamResolverMetadata() {
    }
}





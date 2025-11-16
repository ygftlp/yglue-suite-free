package org.yglue.flow.runtime.core.param.resolvers;

import org.yglue.flow.annotations.FlowResolver;

/**
 * 内置表达式解析器元数据。
 */
@FlowResolver(
        value = "EXPRESSION",
        name = "表达式",
        description = "通过 SpEL / Groovy 表达式组合请求与上下文数据",
        category = "表达式",
        builtin = true
)
public final class ExpressionParamResolverMetadata {
    private ExpressionParamResolverMetadata() {
    }
}





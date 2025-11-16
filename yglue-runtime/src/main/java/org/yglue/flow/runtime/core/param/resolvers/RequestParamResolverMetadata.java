package org.yglue.flow.runtime.core.param.resolvers;

import org.yglue.flow.annotations.FlowResolver;

/**
 * 内置请求参数解析器元数据。
 * <p>
 * 实际解析逻辑由 {@link org.yglue.flow.runtime.core.util.ParamResolver} 提供，
 * 该类仅用于暴露注解元数据，便于 IDE 插件识别。
 */
@FlowResolver(
        value = "REQUEST",
        name = "请求参数",
        description = "从 HTTP 请求体、路径、查询、Header、Form 等位置提取字段",
        category = "HTTP",
        builtin = true
)
public final class RequestParamResolverMetadata {
    private RequestParamResolverMetadata() {
    }
}





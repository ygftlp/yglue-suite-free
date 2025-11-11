package org.yglue.flow.runtime.core.param.resolvers;

import org.yglue.flow.annotations.FlowResolver;

/**
 * 内置常量解析器元数据。
 */
@FlowResolver(
        value = "CONSTANT",
        name = "常量",
        description = "使用固定常量值作为节点输入",
        category = "常量",
        builtin = true
)
public final class ConstantParamResolverMetadata {
    private ConstantParamResolverMetadata() {
    }
}


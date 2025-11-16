package org.yglue.flow.runtime.core.param;

/**
 * 参数解析器核心接口。
 */
public interface ParamResolver {

    /**
     * 解析单个指令。
     */
    ResolveResult<?> resolve(ResolveInstruction instruction, ResolveContext context);

    /**
     * 批量解析，可根据实现优化执行效率。
     */
    default ParamResolveBatchResult resolveBatch(Iterable<ResolveInstruction> instructions, ResolveContext context) {
        ParamResolveBatchResult.Builder builder = ParamResolveBatchResult.builder();
        for (ResolveInstruction instruction : instructions) {
            ResolveResult<?> result = resolve(instruction, context);
            builder.put(instruction.name(), result);
        }
        return builder.build();
    }
}





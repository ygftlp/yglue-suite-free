package org.yglue.flow.runtime.core;
import lombok.Getter;
import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

@Getter
public class NodeExecutionContext {
    private final FlowDefinition flow;
    private final NodeDefinition node;
    private final FlowContext context;

    public NodeExecutionContext(FlowDefinition flow,
                                NodeDefinition node,
                                FlowContext context) {
        this.flow = flow;
        this.node = node;
        this.context = context;
    }

    public void executeChildren(Iterable<NodeDefinition> nodes) {
        // 移除对 ChildNodeRunner 的依赖
        // 在 LiteFlow 引擎中，子节点执行由框架自动处理
        // 这里留空或者可以添加日志记录
    }
}

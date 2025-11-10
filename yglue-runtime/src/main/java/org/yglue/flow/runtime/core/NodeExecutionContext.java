package org.yglue.flow.runtime.core;
import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

public class NodeExecutionContext {
    private final FlowDefinition flow;
    private final NodeDefinition node;
    private final FlowContext context;
    private final ChildNodeRunner childRunner;

    public NodeExecutionContext(FlowDefinition flow,
                                NodeDefinition node,
                                FlowContext context,
                                ChildNodeRunner childRunner) {
        this.flow = flow;
        this.node = node;
        this.context = context;
        this.childRunner = childRunner;
    }

    public FlowDefinition getFlow() {
        return flow;
    }

    public NodeDefinition getNode() {
        return node;
    }

    public FlowContext getContext() {
        return context;
    }

    public void executeChildren(Iterable<NodeDefinition> nodes) {
        if (childRunner == null || nodes == null) {
            return;
        }
        if (nodes instanceof java.util.List<NodeDefinition> list) {
            childRunner.run(list);
        } else {
            java.util.ArrayList<NodeDefinition> list = new java.util.ArrayList<>();
            nodes.forEach(list::add);
            childRunner.run(list);
        }
    }
}

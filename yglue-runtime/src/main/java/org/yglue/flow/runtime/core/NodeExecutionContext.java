package org.yglue.flow.runtime.core;

import lombok.Getter;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.function.Consumer;

@Getter
public class NodeExecutionContext {
    private final FlowDefinition flow;
    private final NodeDefinition node;
    private final FlowContext context;
    private final Consumer<NodeDefinition> childRunner;

    public NodeExecutionContext(FlowDefinition flow,
            NodeDefinition node,
            FlowContext context,
            Consumer<NodeDefinition> childRunner) {
        this.flow = flow;
        this.node = node;
        this.context = context;
        this.childRunner = childRunner;
    }

    public void executeChildren(Iterable<NodeDefinition> nodes) {
        if (nodes != null && childRunner != null) {
            for (NodeDefinition child : nodes) {
                childRunner.accept(child);
            }
        }
    }
}

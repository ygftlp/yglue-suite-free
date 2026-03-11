package org.yglue.flow.runtime.core.engine;

import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.List;
import java.util.function.Consumer;

public class DefaultNodeInterceptorChain implements NodeInterceptorChain {

    private final List<NodeInterceptor> interceptors;
    private final FlowDefinition flow;
    private final FlowContext context;
    private final NodeDefinition node;
    private final FlowExecutor targetExecutor;
    private final Consumer<NodeDefinition> childRunner;
    private int currentIndex = 0;

    public DefaultNodeInterceptorChain(List<NodeInterceptor> interceptors,
            FlowDefinition flow,
            FlowContext context,
            NodeDefinition node,
            FlowExecutor targetExecutor,
            Consumer<NodeDefinition> childRunner) {
        this.interceptors = interceptors;
        this.flow = flow;
        this.context = context;
        this.node = node;
        this.targetExecutor = targetExecutor;
        this.childRunner = childRunner;
    }

    @Override
    public FlowContext getContext() {
        return context;
    }

    @Override
    public NodeDefinition getNode() {
        return node;
    }

    @Override
    public Object proceed() throws Exception {
        if (currentIndex < interceptors.size()) {
            NodeInterceptor interceptor = interceptors.get(currentIndex++);
            return interceptor.intercept(context, node, this);
        }

        NodeExecutionContext executionContext = new NodeExecutionContext(
                flow,
                node,
                context,
                childRunner);
        return targetExecutor.execute(executionContext);
    }
}

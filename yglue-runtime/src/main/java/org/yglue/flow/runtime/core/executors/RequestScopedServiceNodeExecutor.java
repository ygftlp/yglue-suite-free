package org.yglue.flow.runtime.core.executors;

import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RequestScopedServiceNodeExecutor implements FlowExecutor {

    private final FlowExecutor delegate;

    public RequestScopedServiceNodeExecutor(FlowExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object execute(NodeExecutionContext context) throws Exception {
        if (context == null || context.getNode() == null || context.getContext() == null) {
            return delegate.execute(context);
        }

        List<Object> resolvedArgs = context.getContext().getResolvedArgs(context.getNode().getId());
        if (resolvedArgs == null) {
            return delegate.execute(context);
        }

        Map<String, Object> mergedConfig = new LinkedHashMap<>(context.getNode().getConfig());
        mergedConfig.put("_resolvedArgs", resolvedArgs);

        NodeDefinition requestScopedNode = new NodeDefinition(
                context.getNode().getId(),
                context.getNode().getType(),
                mergedConfig,
                context.getNode().getChildren());

        NodeExecutionContext requestScopedContext = new NodeExecutionContext(
                context.getFlow(),
                requestScopedNode,
                context.getContext(),
                context.getChildRunner());

        return delegate.execute(requestScopedContext);
    }
}

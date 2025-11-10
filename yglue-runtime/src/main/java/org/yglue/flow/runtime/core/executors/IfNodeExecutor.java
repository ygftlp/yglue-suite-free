package org.yglue.flow.runtime.core.executors;

import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;

import java.util.List;
import java.util.Map;

public class IfNodeExecutor implements NodeExecutor {
    @Override
    public Object execute(NodeExecutionContext context) {
        Object condition = context.getNode().getConfig().get("condition");
        boolean result = ExpressionEvaluator.evaluateBoolean(condition, context.getContext());
        Object blocks = context.getNode().getConfig().get("blocks");
        if (blocks instanceof Map<?, ?> map) {
            List<NodeDefinition> thenNodes = (List<NodeDefinition>) map.get("then");
            List<NodeDefinition> elseNodes = (List<NodeDefinition>) map.get("else");
            if (result && thenNodes != null) {
                context.executeChildren(thenNodes);
            } else if (!result && elseNodes != null) {
                context.executeChildren(elseNodes);
            }
        }
        return result;
    }
}

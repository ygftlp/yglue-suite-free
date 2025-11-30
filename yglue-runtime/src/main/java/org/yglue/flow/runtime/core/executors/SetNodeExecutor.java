package org.yglue.flow.runtime.core.executors;

import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;

import java.util.Map;

public class SetNodeExecutor implements FlowExecutor {
    @Override
    public Object execute(NodeExecutionContext context) {
        Map<String, Object> config = context.getNode().getConfig();
        String target = String.valueOf(config.get("target"));
        Object valueExpression = config.get("value");
        Object value = ExpressionEvaluator.evaluate(valueExpression, context.getContext());
        context.getContext().set(target, value);
        return value;
    }
}

package org.yglue.flow.runtime.core.executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;

public class LogNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(LogNodeExecutor.class);

    @Override
    public Object execute(NodeExecutionContext context) {
        Object message = context.getNode().getConfig().getOrDefault("message", "");
        Object evaluated = ExpressionEvaluator.evaluate(message, context.getContext());
        log.info("[yglue] {}", evaluated);
        return evaluated;
    }
}

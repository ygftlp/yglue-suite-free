package org.yglue.flow.runtime.core.executors;

import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutor;

public class DelayNodeExecutor implements NodeExecutor {
    @Override
    public Object execute(NodeExecutionContext context) throws InterruptedException {
        Object millis = context.getNode().getConfig().getOrDefault("millis", 0);
        long duration = millis instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(millis));
        if (duration > 0) {
            Thread.sleep(duration);
        }
        return duration;
    }
}

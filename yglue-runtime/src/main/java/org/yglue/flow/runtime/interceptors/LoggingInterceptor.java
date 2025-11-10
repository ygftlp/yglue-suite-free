package org.yglue.flow.runtime.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.ExecutionInterceptor;
import org.yglue.flow.runtime.core.NodeExecutionContext;

public class LoggingInterceptor implements ExecutionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public void beforeNode(NodeExecutionContext context) {
        log.debug("Executing node {} ({})", context.getNode().getId(), context.getNode().getType());
    }

    @Override
    public void afterNode(NodeExecutionContext context, Object result) {
        log.debug("Node {} completed with result {}", context.getNode().getId(), result);
    }

    @Override
    public void onError(NodeExecutionContext context, Exception ex) {
        log.warn("Node {} failed: {}", context.getNode().getId(), ex.getMessage());
    }
}

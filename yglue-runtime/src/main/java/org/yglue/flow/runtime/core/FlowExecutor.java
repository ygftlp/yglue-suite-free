package org.yglue.flow.runtime.core;

public interface FlowExecutor {
    Object execute(NodeExecutionContext context) throws Exception;
}

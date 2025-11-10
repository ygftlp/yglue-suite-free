package org.yglue.flow.runtime.core;

public interface NodeExecutor {
    Object execute(NodeExecutionContext context) throws Exception;
}

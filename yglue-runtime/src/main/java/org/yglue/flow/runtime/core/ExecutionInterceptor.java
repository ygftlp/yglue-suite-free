package org.yglue.flow.runtime.core;

public interface ExecutionInterceptor {

    default void beforeNode(NodeExecutionContext context) {
    }

    default void afterNode(NodeExecutionContext context, Object result) {
    }

    default void onError(NodeExecutionContext context, Exception ex) {
    }
}

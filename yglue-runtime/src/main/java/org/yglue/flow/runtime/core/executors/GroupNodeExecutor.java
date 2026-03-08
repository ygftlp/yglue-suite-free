package org.yglue.flow.runtime.core.executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.List;

/**
 * 组节点（事务组 / 服务组）执行器
 * 该执行器在接收到 GroupNode 调度时，会【同步地、按顺序地】依次执行所有子节点。
 * 这种模式下，所有子节点与 GroupNode 本身处于同一线程中，能够完美复用外层开启的 Spring TransactionTemplate，
 * 从而实现跨节点的全局本地事务一致性回滚。
 */
public class GroupNodeExecutor implements FlowExecutor {
    private static final Logger log = LoggerFactory.getLogger(GroupNodeExecutor.class);

    @Override
    public Object execute(NodeExecutionContext context) {
        String nodeId = context.getNode().getId();
        log.info("[GroupNodeExecutor] 开始同步执行节点组: {}", nodeId);

        List<NodeDefinition> children = context.getNode().getChildren();
        if (children == null || children.isEmpty()) {
            log.warn("[GroupNodeExecutor] 组内没有任何子节点, NodeID: {}", nodeId);
            return null;
        }

        // 委托 NodeExecutionContext 原生回调 GraphExecutor 依次回调 children
        try {
            context.executeChildren(children);
            log.info("[GroupNodeExecutor] 节点组执行完成: {}", nodeId);
        } catch (Exception e) {
            log.error("[GroupNodeExecutor] 节点组执行内部发生异常: {}, 触发回滚", nodeId, e);
            throw new RuntimeException("Group execution failed for node " + nodeId + ", rollback triggered.", e);
        }

        // 返回 context 内已累积的上下文数据，代表组成功完成
        return context.getContext().getVariables();
    }
}

package org.yglue.flow.runtime.core.engine;

import org.yglue.flow.runtime.core.definition.FlowNode;

/**
 * 节点执行拦截器链
 */
public interface NodeInterceptorChain {

    /**
     * @return 流程全局上下文
     */
    FlowContext getContext();

    /**
     * @return 当前执行的节点
     */
    FlowNode getNode();

    /**
     * 继续执行下一个拦截器，或最终执行原生的节点核心逻辑
     * 
     * @return 执行结果
     * @throws Exception 异常
     */
    Object proceed() throws Exception;
}

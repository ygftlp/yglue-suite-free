package org.yglue.flow.runtime.core.engine;

import org.yglue.flow.runtime.core.definition.FlowNode;

/**
 * 节点执行拦截器接口
 * 采用职责链模式（Chain of Responsibility）处理节点的执行生命周期
 */
public interface NodeInterceptor {

    /**
     * 拦截节点执行
     * 
     * @param context 当前流程上下文
     * @param node    当前执行的节点定义
     * @param chain   拦截器链，调用 chain.proceed() 继续执行下一个拦截器
     * @return 节点执行的返回值（如果有）
     * @throws Exception 执行过程中可能抛出的异常
     */
    Object intercept(FlowContext context, FlowNode node, NodeInterceptorChain chain) throws Exception;

}

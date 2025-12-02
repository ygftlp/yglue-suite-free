package org.yglue.flow.runtime.liteflow.adapter;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

/**
 * 服务节点组件
 * 这是一个通用的 LiteFlow 组件，用于执行服务调用类型的节点（如 task、transformer 等）
 * 根据组件 ID 动态路由到对应的 NodeExecutor
 * 
 * @author yglue
 * @since 1.0
 */
@LiteflowComponent("serviceNode")
public class ServiceNodeComponent extends FlowNodeComponent {

    private static final Logger log = LoggerFactory.getLogger(ServiceNodeComponent.class);

    @Override
    public void process() throws Exception {
        // 获取实际的节点ID（即组件ID）
        String componentId = getActualNodeId();
        
        // 确保 componentId 不为空
        if (componentId == null || componentId.isEmpty()) {
            log.error("[ServiceNodeComponent] 组件ID为空，无法执行组件");
            throw new IllegalArgumentException("Component ID is null or empty");
        }
        
        log.debug("[ServiceNodeComponent] 开始执行组件: componentId={}", componentId);
        
        // 检查executorRegistry是否已注入
        if (executorRegistry == null) {
            log.error("[ServiceNodeComponent] NodeExecutorRegistry 未注入，无法执行组件: componentId={}", componentId);
            throw new IllegalStateException("NodeExecutorRegistry is not available");
        }
        
        // 从注册表中获取组件信息
        ComponentInfo info = getComponentInfo(componentId);
        if (info == null) {
            log.error("[ServiceNodeComponent] 组件未注册: componentId={}", componentId);
            // 提供更详细的错误信息
            log.error("[ServiceNodeComponent] 当前注册的组件: {}", getRegisteredComponentIds());
            throw new IllegalArgumentException("Component not registered: " + componentId);
        }
        
        NodeDefinition nodeDefinition = info.nodeDefinition;
        FlowDefinition flowDefinition = info.flowDefinition;
        
        log.debug("[ServiceNodeComponent] 找到组件信息: componentId={}, nodeType={}, nodeId={}", 
                componentId, nodeDefinition.getType(), nodeDefinition.getId());
        
        // 从 LiteFlow 的 Context 中获取或创建 FlowContext
        FlowContext flowContext = getFlowContext();
        
        // 执行节点
        executeNode(componentId, nodeDefinition, flowDefinition, flowContext);
    }
    
    @Override
    protected boolean isFixedComponentId(String nodeId) {
        return "serviceNode".equals(nodeId);
    }
    
    @Override
    protected String getDefaultComponentId() {
        return "serviceNode";
    }
    
    @Override
    protected void executeNode(
            String componentId, 
            NodeDefinition nodeDefinition, 
            FlowDefinition flowDefinition, 
            FlowContext flowContext) throws Exception {
        
        // 获取对应的执行器
        org.yglue.flow.runtime.core.FlowExecutor executor = executorRegistry.get(nodeDefinition.getType());
        log.debug("[ServiceNodeComponent] 获取执行器: componentId={}, executorType={}", 
                componentId, executor != null ? executor.getClass().getName() : "null");
        
        // 检查执行器是否存在
        if (executor == null) {
            log.error("[ServiceNodeComponent] 未找到对应的执行器: componentId={}, nodeType={}", 
                    componentId, nodeDefinition.getType());
            throw new IllegalStateException("Executor not found for node type: " + nodeDefinition.getType());
        }
        
        // 创建 NodeExecutionContext
        NodeExecutionContext executionContext = new NodeExecutionContext(
                flowDefinition,
                nodeDefinition,
                flowContext
        );
        
        // 执行节点
        log.info("[ServiceNodeComponent] 执行节点: componentId={}, nodeType={}, nodeId={}", 
                componentId, nodeDefinition.getType(), nodeDefinition.getId());
        Object result = executor.execute(executionContext);
        
        // 将结果保存到 LiteFlow Context
        if (result != null) {
            saveResultToContext(result, componentId);
            log.debug("[ServiceNodeComponent] 保存结果到 LiteFlow Context: componentId={}, resultType={}", 
                    componentId, result.getClass().getName());
        }
        
        log.info("[ServiceNodeComponent] 组件执行完成: componentId={}, nodeType={}, nodeId={}", 
                componentId, nodeDefinition.getType(), nodeDefinition.getId());
    }
}


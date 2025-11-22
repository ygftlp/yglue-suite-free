package org.yglue.flow.runtime.liteflow.adapter;

import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NodeExecutorComponent 工厂
 * 负责创建和管理 NodeExecutorComponent 实例
 */
public class NodeExecutorComponentFactory {

    private final NodeExecutorRegistry executorRegistry;
    private final ApplicationContext applicationContext;
    
    /**
     * 组件缓存
     * key: componentId (type_nodeId), value: NodeExecutorComponent
     */
    private final Map<String, NodeExecutorComponent> componentCache = new ConcurrentHashMap<>();

    public NodeExecutorComponentFactory(NodeExecutorRegistry executorRegistry,
                                        ApplicationContext applicationContext) {
        this.executorRegistry = executorRegistry;
        this.applicationContext = applicationContext;
    }

    /**
     * 创建或获取 NodeExecutorComponent
     */
    public NodeExecutorComponent getOrCreateComponent(NodeDefinition nodeDefinition,
                                                      FlowDefinition flowDefinition) {
        String componentId = getComponentId(nodeDefinition);
        
        return componentCache.computeIfAbsent(componentId, id -> {
            NodeExecutor executor = executorRegistry.get(nodeDefinition.getType());
            return new NodeExecutorComponent(executor, nodeDefinition, flowDefinition);
        });
    }

    /**
     * 获取组件 ID
     */
    private String getComponentId(NodeDefinition node) {
        return node.getType().toLowerCase() + "_" + node.getId();
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        componentCache.clear();
    }
}




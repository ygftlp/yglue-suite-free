package org.yglue.flow.runtime.liteflow.adapter;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务节点组件
 * 这是一个通用的 LiteFlow 组件，用于执行服务调用类型的节点（如 task、transformer 等）
 * 根据组件 ID 动态路由到对应的 NodeExecutor
 * 
 * 组件 ID 格式：{type}_{nodeId}
 * 例如：task_node1, transformer_node2
 * 
 * 使用方式：
 * 1. 在 FlowDefinitionToLiteFlowConverter 中，将节点转换为组件 ID
 * 2. 在转换时，将节点定义存储到 componentRegistry 中
 * 3. 当 LiteFlow 执行时，通过组件 ID 找到对应的节点定义和执行器
 * 
 * 注意：后续可以考虑为每种节点类型创建专门的组件（如 TaskNodeComponent、TransformerNodeComponent 等）
 * 这样可以更好地支持不同类型的节点，例如 HttpNodeComponent 用于 HTTP 调用
 */
@LiteflowComponent("serviceNode")
public class ServiceNodeComponent extends NodeComponent {

    private static final Logger log = LoggerFactory.getLogger(ServiceNodeComponent.class);

    @Autowired(required = false)
    private NodeExecutorRegistry executorRegistry;

    /**
     * 组件注册表
     * key: componentId (type_nodeId), value: 节点定义和流程定义的组合
     */
    private static final Map<String, ComponentInfo> componentRegistry = new ConcurrentHashMap<>();

    /**
     * 注册组件信息
     * 在转换流程定义时调用，将节点信息注册到组件注册表中
     */
    public static void registerComponent(String componentId, NodeDefinition nodeDefinition, FlowDefinition flowDefinition) {
        componentRegistry.put(componentId, new ComponentInfo(nodeDefinition, flowDefinition));
        log.debug("[ServiceNodeComponent] 注册组件: componentId={}, nodeType={}, nodeId={}", 
                componentId, nodeDefinition.getType(), nodeDefinition.getId());
    }

    /**
     * 取消注册组件
     */
    public static void unregisterComponent(String componentId) {
        componentRegistry.remove(componentId);
        log.debug("[ServiceNodeComponent] 取消注册组件: componentId={}", componentId);
    }

    /**
     * 清除所有注册的组件
     */
    public static void clearAll() {
        componentRegistry.clear();
        log.debug("[ServiceNodeComponent] 清除所有组件注册");
    }

    @Override
    public void process() throws Exception {
        // 获取组件 ID
        // 方式1：从 tag 获取（如果使用了 tag）
        String componentId = this.getTag();
        if (componentId == null || componentId.isEmpty()) {
            // 方式2：从 nodeId 获取（如果直接使用组件 ID）
            componentId = this.getNodeId();
        }
        log.debug("[ServiceNodeComponent] 开始执行组件: componentId={}, nodeId={}, tag={}", 
                componentId, this.getNodeId(), this.getTag());

        if (executorRegistry == null) {
            log.error("[ServiceNodeComponent] NodeExecutorRegistry 未注入，无法执行组件: componentId={}", componentId);
            throw new IllegalStateException("NodeExecutorRegistry is not available");
        }

        // 从注册表中获取组件信息
        ComponentInfo info = componentRegistry.get(componentId);
        if (info == null) {
            log.error("[ServiceNodeComponent] 组件未注册: componentId={}", componentId);
            throw new IllegalArgumentException("Component not registered: " + componentId);
        }

        NodeDefinition nodeDefinition = info.nodeDefinition;
        FlowDefinition flowDefinition = info.flowDefinition;

        log.debug("[ServiceNodeComponent] 找到组件信息: componentId={}, nodeType={}, nodeId={}", 
                componentId, nodeDefinition.getType(), nodeDefinition.getId());

        // 从 LiteFlow 的 Context 中获取或创建 FlowContext
        FlowContext flowContext = getFlowContext();

        // 获取对应的执行器
        NodeExecutor executor = executorRegistry.get(nodeDefinition.getType());
        log.debug("[ServiceNodeComponent] 获取执行器: componentId={}, executorType={}", 
                componentId, executor != null ? executor.getClass().getName() : "null");

        // 创建 NodeExecutionContext
        NodeExecutionContext executionContext = new NodeExecutionContext(
                flowDefinition,
                nodeDefinition,
                flowContext,
                children -> {
                    // 子节点执行逻辑由 LiteFlow 处理
                }
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

    /**
     * 从 LiteFlow Context 中获取或创建 FlowContext
     */
    private FlowContext getFlowContext() {
        // 尝试从 LiteFlow Context 中获取 FlowContext
        Object contextObj = getContextBeanSafely();

        if (contextObj instanceof FlowContext) {
            return (FlowContext) contextObj;
        }

        // 如果没有，创建一个新的 FlowContext
        FlowContext flowContext = new FlowContext();

        // 从 LiteFlow Context 中提取数据
        if (contextObj != null) {
            try {
                Map<String, Object> data = extractDataFromContext(contextObj);
                if (data != null) {
                    flowContext.data().putAll(data);
                }
            } catch (Exception e) {
                log.warn("[ServiceNodeComponent] 从 LiteFlow Context 提取数据失败", e);
            }
        }

        return flowContext;
    }

    /**
     * 安全地获取 LiteFlow Context Bean
     * 尝试多种方式获取上下文对象
     */
    private Object getContextBeanSafely() {
        try {
            // 方式1：尝试无参的 getContextBean() 方法
            try {
                java.lang.reflect.Method getContextBeanMethod = this.getClass().getSuperclass()
                        .getMethod("getContextBean");
                return getContextBeanMethod.invoke(this);
            } catch (NoSuchMethodException e) {
                // 方式2：尝试 getFirstContextBean() 方法
                try {
                    java.lang.reflect.Method getFirstContextBeanMethod = this.getClass().getSuperclass()
                            .getMethod("getFirstContextBean");
                    return getFirstContextBeanMethod.invoke(this);
                } catch (NoSuchMethodException ex) {
                    // 方式3：尝试通过反射获取 slot 或 context
                    try {
                        java.lang.reflect.Field slotField = this.getClass().getSuperclass().getDeclaredField("slot");
                        slotField.setAccessible(true);
                        Object slot = slotField.get(this);
                        if (slot != null) {
                            // 尝试从 slot 中获取 context
                            java.lang.reflect.Method getContextMethod = slot.getClass().getMethod("getContext");
                            return getContextMethod.invoke(slot);
                        }
                    } catch (Exception ignored) {
                        // 忽略所有异常
                    }
                    log.debug("[ServiceNodeComponent] 无法获取 Context Bean，返回 null");
                    return null;
                }
            }
        } catch (Exception e) {
            log.debug("[ServiceNodeComponent] 获取 Context Bean 时出错: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 LiteFlow Context 中提取数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDataFromContext(Object contextObj) {
        try {
            if (contextObj instanceof Map) {
                return (Map<String, Object>) contextObj;
            }

            // 尝试调用 getData() 方法
            java.lang.reflect.Method getDataMethod = contextObj.getClass().getMethod("getData");
            Object data = getDataMethod.invoke(contextObj);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
        } catch (Exception e) {
            log.debug("[ServiceNodeComponent] 无法从 Context 提取数据", e);
        }

        return null;
    }

    /**
     * 将执行结果保存到 LiteFlow Context
     * 在 LiteFlow 2.12.1 中，使用 Context 的 setData 方法来存储数据
     */
    private void saveResultToContext(Object result, String componentId) {
        try {
            // 获取 LiteFlow Context
            Object contextObj = getContextBeanSafely();
            if (contextObj == null) {
                log.debug("[ServiceNodeComponent] Context 为空，无法保存结果: componentId={}", componentId);
                return;
            }

            // 尝试调用 setData 方法保存结果
            // LiteFlow Context 通常有 setData(String key, Object value) 方法
            try {
                java.lang.reflect.Method setDataMethod = contextObj.getClass()
                        .getMethod("setData", String.class, Object.class);
                // 使用组件 ID 作为 key，或者使用固定的 key
                setDataMethod.invoke(contextObj, componentId, result);
                log.debug("[ServiceNodeComponent] 通过 setData 保存结果: componentId={}", componentId);
            } catch (NoSuchMethodException e) {
                // 如果没有 setData 方法，尝试其他方式
                // 可能 Context 本身就是 Map，或者有 put 方法
                if (contextObj instanceof Map) {
                    ((Map<String, Object>) contextObj).put(componentId, result);
                    log.debug("[ServiceNodeComponent] 通过 Map.put 保存结果: componentId={}", componentId);
                } else {
                    // 尝试调用 put 方法
                    try {
                        java.lang.reflect.Method putMethod = contextObj.getClass()
                                .getMethod("put", String.class, Object.class);
                        putMethod.invoke(contextObj, componentId, result);
                        log.debug("[ServiceNodeComponent] 通过 put 方法保存结果: componentId={}", componentId);
                    } catch (Exception ex) {
                        log.warn("[ServiceNodeComponent] 无法保存结果到 Context: componentId={}, error={}", 
                                componentId, ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ServiceNodeComponent] 保存结果到 Context 失败: componentId={}, error={}", 
                    componentId, e.getMessage());
        }
    }

    /**
     * 组件信息
     */
    private static class ComponentInfo {
        final NodeDefinition nodeDefinition;
        final FlowDefinition flowDefinition;

        ComponentInfo(NodeDefinition nodeDefinition, FlowDefinition flowDefinition) {
            this.nodeDefinition = nodeDefinition;
            this.flowDefinition = flowDefinition;
        }
    }
}


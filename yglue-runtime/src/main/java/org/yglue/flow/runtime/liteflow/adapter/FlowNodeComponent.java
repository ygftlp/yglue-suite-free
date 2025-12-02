package org.yglue.flow.runtime.liteflow.adapter;

import com.yomahub.liteflow.core.NodeComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程节点组件抽象基类
 * 提供通用的节点注册、执行器注入等功能
 * 
 * <p>所有自定义的 LiteFlow 节点组件都应该继承此类，例如：</p>
 * <ul>
 *   <li>ServiceNodeComponent - 服务调用节点</li>
 *   <li>GroovyNodeComponent - Groovy脚本节点</li>
 *   <li>TransformerNodeComponent - 数据转换节点</li>
 * </ul>
 * 
 * @author yglue
 * @since 1.0
 */
public abstract class FlowNodeComponent extends NodeComponent {

    private static final Logger log = LoggerFactory.getLogger(FlowNodeComponent.class);

    /**
     * 节点执行器注册表
     * 由Spring自动注入，用于根据节点类型获取对应的执行器
     */
    @Autowired(required = false)
    protected NodeExecutorRegistry executorRegistry;

    /**
     * 组件注册表
     * key: componentId (节点ID), value: 节点定义和流程定义的组合
     * 
     * 所有继承此类的组件共享同一个注册表
     */
    private static final Map<String, ComponentInfo> componentRegistry = new ConcurrentHashMap<>();

    /**
     * 组件信息
     * 存储节点定义和流程定义
     */
    protected static class ComponentInfo {
        final NodeDefinition nodeDefinition;
        final FlowDefinition flowDefinition;

        ComponentInfo(NodeDefinition nodeDefinition, FlowDefinition flowDefinition) {
            this.nodeDefinition = nodeDefinition;
            this.flowDefinition = flowDefinition;
        }
    }

    /**
     * 注册组件信息
     * 在转换流程定义时调用，将节点信息注册到组件注册表中
     * 
     * @param componentId 组件ID（节点ID）
     * @param nodeDefinition 节点定义
     * @param flowDefinition 流程定义
     */
    public static void registerComponent(String componentId, NodeDefinition nodeDefinition, FlowDefinition flowDefinition) {
        componentRegistry.put(componentId, new ComponentInfo(nodeDefinition, flowDefinition));
        log.debug("[FlowNodeComponent] 注册组件: componentId={}, nodeType={}, nodeId={}", 
                componentId, nodeDefinition.getType(), nodeDefinition.getId());
    }

    /**
     * 取消注册组件
     * 
     * @param componentId 组件ID
     */
    public static void unregisterComponent(String componentId) {
        componentRegistry.remove(componentId);
        log.debug("[FlowNodeComponent] 取消注册组件: componentId={}", componentId);
    }

    /**
     * 清除所有注册的组件
     */
    public static void clearAll() {
        componentRegistry.clear();
        log.debug("[FlowNodeComponent] 清除所有组件注册");
    }

    /**
     * 获取组件信息
     * 
     * @param componentId 组件ID
     * @return 组件信息，如果不存在返回 null
     */
    protected static ComponentInfo getComponentInfo(String componentId) {
        return componentRegistry.get(componentId);
    }

    /**
     * 检查组件是否已注册
     * 
     * @param componentId 组件ID
     * @return 如果已注册返回 true，否则返回 false
     */
    protected static boolean isComponentRegistered(String componentId) {
        return componentRegistry.containsKey(componentId);
    }

    /**
     * 获取当前注册的所有组件ID
     * 
     * @return 组件ID集合
     */
    protected static java.util.Set<String> getRegisteredComponentIds() {
        return componentRegistry.keySet();
    }

    /**
     * 获取实际的节点ID
     * 由于我们使用固定的组件ID（如 serviceNode），需要通过其他方式获取实际的节点ID
     * 
     * @return 实际的节点ID
     */
    protected String getActualNodeId() {
        // 首先尝试使用getNodeId()获取节点ID
        try {
            String nodeId = this.getNodeId();
            if (nodeId != null && !nodeId.isEmpty() && !isFixedComponentId(nodeId)) {
                return nodeId;
            }
        } catch (Exception e) {
            log.debug("[FlowNodeComponent] 无法通过getNodeId获取节点ID: {}", e.getMessage());
        }
        
        // 如果getNodeId()无法获取到实际的节点ID，尝试从LiteFlow的上下文中获取节点ID
        try {
            // 获取当前节点的标签(Tag)
            String tag = this.getTag();
            if (tag != null && !tag.isEmpty()) {
                return tag;
            }
        } catch (Exception e) {
            log.debug("[FlowNodeComponent] 无法通过getTag获取节点ID: {}", e.getMessage());
        }
        
        // 返回默认值（子类可以覆盖此方法提供特定的默认值）
        return getDefaultComponentId();
    }

    /**
     * 判断是否是固定的组件ID
     * 子类应该覆盖此方法，返回自己的固定组件ID
     * 
     * @param nodeId 节点ID
     * @return 如果是固定组件ID返回 true
     */
    protected abstract boolean isFixedComponentId(String nodeId);

    /**
     * 获取默认的组件ID
     * 子类应该覆盖此方法，返回自己的默认组件ID
     * 
     * @return 默认组件ID
     */
    protected abstract String getDefaultComponentId();

    /**
     * 从 LiteFlow Context 中获取或创建 FlowContext
     * 
     * @return FlowContext 实例
     */
    protected FlowContext getFlowContext() {
        // 尝试从 LiteFlow Context 中获取 FlowContext
        Object contextObj = getContextBeanSafely();
        
        // 检查contextObj是否是LiteFlow内部对象
        if (contextObj != null) {
            String contextClassName = contextObj.getClass().getName();
            if (contextClassName.startsWith("com.yomahub.liteflow.")) {
                log.debug("[FlowNodeComponent] Context是LiteFlow内部对象，创建新的FlowContext: className={}", contextClassName);
                contextObj = null;
            }
        }

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
                log.warn("[FlowNodeComponent] 从 LiteFlow Context 提取数据失败", e);
            }
        }

        return flowContext;
    }

    /**
     * 安全地获取 LiteFlow Context Bean
     * 
     * @return Context对象，如果获取失败返回 null
     */
    protected Object getContextBeanSafely() {
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
                    log.debug("[FlowNodeComponent] 无法获取 Context Bean，返回 null");
                    return null;
                }
            }
        } catch (Exception e) {
            log.warn("[FlowNodeComponent] 获取 Context Bean 时发生异常", e);
            return null;
        }
    }

    /**
     * 从 LiteFlow Context 提取数据
     * 
     * @param contextObj Context对象
     * @return 提取的数据Map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractDataFromContext(Object contextObj) {
        try {
            // 尝试调用 getData 方法
            java.lang.reflect.Method getDataMethod = contextObj.getClass().getMethod("getData");
            Object data = getDataMethod.invoke(contextObj);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
        } catch (Exception e) {
            log.debug("[FlowNodeComponent] 无法从 Context 提取数据: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 将结果保存到 LiteFlow Context
     * 
     * @param result 执行结果
     * @param componentId 组件ID
     */
    protected void saveResultToContext(Object result, String componentId) {
        // 如果结果为空，不保存
        if (result == null) {
            log.debug("[FlowNodeComponent] 结果为空，不保存到 Context: componentId={}", componentId);
            return;
        }
        
        // 检查结果是否是可以序列化的类型
        // 避免保存LiteFlow内部对象，如DefaultContext等
        if (result instanceof com.yomahub.liteflow.slot.DefaultContext) {
            log.debug("[FlowNodeComponent] 结果是LiteFlow内部对象，不保存到 Context: componentId={}", componentId);
            return;
        }
        
        // 检查结果是否是其他LiteFlow内部类
        String resultClassName = result.getClass().getName();
        if (resultClassName.startsWith("com.yomahub.liteflow.")) {
            log.debug("[FlowNodeComponent] 结果是LiteFlow内部类，不保存到 Context: componentId={}, className={}", 
                    componentId, resultClassName);
            return;
        }
        
        try {
            // 获取 LiteFlow Context
            Object contextObj = getContextBeanSafely();
            if (contextObj == null) {
                log.debug("[FlowNodeComponent] Context 为空，无法保存结果: componentId={}", componentId);
                return;
            }
            
            // 尝试调用 setData 方法保存结果
            try {
                java.lang.reflect.Method setDataMethod = contextObj.getClass()
                        .getMethod("setData", String.class, Object.class);
                setDataMethod.invoke(contextObj, componentId, result);
                log.debug("[FlowNodeComponent] 通过 setData 保存结果: componentId={}", componentId);
            } catch (NoSuchMethodException e) {
                // 如果没有 setData 方法，尝试其他方式
                if (contextObj instanceof Map) {
                    ((Map<String, Object>) contextObj).put(componentId, result);
                    log.debug("[FlowNodeComponent] 通过 Map.put 保存结果: componentId={}", componentId);
                } else {
                    // 尝试调用 put 方法
                    try {
                        java.lang.reflect.Method putMethod = contextObj.getClass()
                                .getMethod("put", String.class, Object.class);
                        putMethod.invoke(contextObj, componentId, result);
                        log.debug("[FlowNodeComponent] 通过 put 方法保存结果: componentId={}", componentId);
                    } catch (Exception ex) {
                        log.warn("[FlowNodeComponent] 无法保存结果到 Context: componentId={}, error={}", 
                                componentId, ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[FlowNodeComponent] 保存结果到 Context 失败: componentId={}, error={}", 
                    componentId, e.getMessage());
        }
    }

    /**
     * 执行节点
     * 子类需要实现此方法来执行具体的节点逻辑
     * 
     * @param componentId 组件ID
     * @param nodeDefinition 节点定义
     * @param flowDefinition 流程定义
     * @param flowContext 流程上下文
     * @throws Exception 执行异常
     */
    protected abstract void executeNode(
            String componentId, 
            NodeDefinition nodeDefinition, 
            FlowDefinition flowDefinition, 
            FlowContext flowContext) throws Exception;
}

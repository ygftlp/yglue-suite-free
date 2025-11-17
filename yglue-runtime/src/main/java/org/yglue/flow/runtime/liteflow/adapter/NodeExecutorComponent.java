package org.yglue.flow.runtime.liteflow.adapter;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.Map;

/**
 * 将 NodeExecutor 适配为 LiteFlow 的 NodeComponent
 * 这是一个通用的适配器，用于将现有的执行器包装成 LiteFlow 组件
 */
@LiteflowComponent
public class NodeExecutorComponent extends NodeComponent {

    private static final Logger log = LoggerFactory.getLogger(NodeExecutorComponent.class);

    private final NodeExecutor executor;
    private final NodeDefinition nodeDefinition;
    private final FlowDefinition flowDefinition;

    public NodeExecutorComponent(NodeExecutor executor, 
                                 NodeDefinition nodeDefinition,
                                 FlowDefinition flowDefinition) {
        this.executor = executor;
        this.nodeDefinition = nodeDefinition;
        this.flowDefinition = flowDefinition;
    }

    @Override
    public void process() throws Exception {
        log.debug("[NodeExecutorComponent] 开始执行节点: nodeId={}, type={}", 
                nodeDefinition.getId(), nodeDefinition.getType());
        
        // 从 LiteFlow 的 Context 中获取 FlowContext
        FlowContext flowContext = getFlowContext();
        
        // 创建 NodeExecutionContext
        NodeExecutionContext executionContext = new NodeExecutionContext(
                flowDefinition,
                nodeDefinition,
                flowContext,
                children -> {
                    // 子节点执行逻辑由 LiteFlow 处理
                    // 这里可以留空或添加额外处理
                }
        );
        
        // 执行节点
        Object result = executor.execute(executionContext);
        
        // 将结果保存到 LiteFlow Context
        if (result != null) {
            saveResultToContext(result);
        }
        
        log.debug("[NodeExecutorComponent] 节点执行完成: nodeId={}, result={}", 
                nodeDefinition.getId(), result);
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
            // 尝试从 contextObj 中提取数据
            // 这里需要根据实际的 LiteFlow Context 实现来调整
            try {
                Map<String, Object> data = extractDataFromContext(contextObj);
                if (data != null) {
                    flowContext.data().putAll(data);
                }
            } catch (Exception e) {
                log.warn("[NodeExecutorComponent] 从 LiteFlow Context 提取数据失败", e);
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
                    log.debug("[NodeExecutorComponent] 无法获取 Context Bean，返回 null");
                    return null;
                }
            }
        } catch (Exception e) {
            log.debug("[NodeExecutorComponent] 获取 Context Bean 时出错: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 LiteFlow Context 中提取数据
     * 这里需要根据实际的 LiteFlow Context 实现来调整
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDataFromContext(Object contextObj) {
        // 尝试通过反射获取数据
        try {
            if (contextObj instanceof Map) {
                return (Map<String, Object>) contextObj;
            }
            
            // 尝试调用 getData() 方法
            java.lang.reflect.Method getDataMethod = contextObj.getClass()
                    .getMethod("getData");
            Object data = getDataMethod.invoke(contextObj);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
        } catch (Exception e) {
            log.debug("[NodeExecutorComponent] 无法从 Context 提取数据", e);
        }
        
        return null;
    }

    /**
     * 将执行结果保存到 LiteFlow Context
     */
    private void saveResultToContext(Object result) {
        try {
            Object contextObj = getContextBeanSafely();
            if (contextObj == null) {
                return;
            }

            // 尝试调用 setData 方法
            try {
                java.lang.reflect.Method setDataMethod = contextObj.getClass()
                        .getMethod("setData", String.class, Object.class);
                setDataMethod.invoke(contextObj, nodeDefinition.getId(), result);
            } catch (NoSuchMethodException e) {
                // 如果没有 setData 方法，尝试其他方式
                if (contextObj instanceof Map) {
                    ((Map<String, Object>) contextObj).put(nodeDefinition.getId(), result);
                } else {
                    try {
                        java.lang.reflect.Method putMethod = contextObj.getClass()
                                .getMethod("put", String.class, Object.class);
                        putMethod.invoke(contextObj, nodeDefinition.getId(), result);
                    } catch (Exception ex) {
                        log.debug("[NodeExecutorComponent] 无法保存结果到 Context", ex);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[NodeExecutorComponent] 保存结果到 Context 失败", e);
        }
    }
}



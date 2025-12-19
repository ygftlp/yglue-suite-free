package org.yglue.flow.runtime.liteflow.adapter;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.List;
import java.util.Map;

/**
 * 服务组节点组件
 * <p>
 * 用于执行服务组节点：
 * <ul>
 *   <li>按子节点顺序依次执行组内服务节点</li>
 *   <li>如果配置了事务（例如 txMode = SPRING），则在统一事务中执行</li>
 * </ul>
 * </p>
 */
@LiteflowComponent("serviceGroup")
public class ServiceGroupNodeComponent extends FlowNodeComponent {

    private static final Logger log = LoggerFactory.getLogger(ServiceGroupNodeComponent.class);

    @Autowired(required = false)
    private ApplicationContext applicationContext;

    @Override
    public void process() throws Exception {
        String componentId = getActualNodeId();
        if (componentId == null || componentId.isEmpty()) {
            log.error("[ServiceGroupNodeComponent] 组件ID为空，无法执行组件");
            throw new IllegalArgumentException("Component ID is null or empty");
        }

        ComponentInfo info = getComponentInfo(componentId);
        if (info == null) {
            log.error("[ServiceGroupNodeComponent] 组件未注册: componentId={}", componentId);
            log.error("[ServiceGroupNodeComponent] 当前注册的组件: {}", getRegisteredComponentIds());
            throw new IllegalArgumentException("Component not registered: " + componentId);
        }

        NodeDefinition groupNode = info.nodeDefinition;
        FlowDefinition flowDefinition = info.flowDefinition;

        FlowContext flowContext = getFlowContext();

        executeNode(componentId, groupNode, flowDefinition, flowContext);
    }

    @Override
    protected boolean isFixedComponentId(String nodeId) {
        return "serviceGroup".equals(nodeId);
    }

    @Override
    protected String getDefaultComponentId() {
        return "serviceGroup";
    }

    @Override
    protected void executeNode(String componentId,
                               NodeDefinition nodeDefinition,
                               FlowDefinition flowDefinition,
                               FlowContext flowContext) throws Exception {
        List<NodeDefinition> children = nodeDefinition.getChildren();
        if (children == null || children.isEmpty()) {
            log.info("[ServiceGroupNodeComponent] 服务组没有子节点, 直接返回: componentId={}", componentId);
            return;
        }

        Map<String, Object> config = nodeDefinition.getConfig();
        String txMode = config != null && config.get("txMode") != null
                ? String.valueOf(config.get("txMode"))
                : "NONE";
        Object txManagerObj = config != null ? config.get("transactionManager") : null;
        String txManagerName = txManagerObj != null ? String.valueOf(txManagerObj) : null;

        Runnable task = () -> {
            for (NodeDefinition child : children) {
                try {
                    executeChildNode(componentId, child, flowDefinition, flowContext);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        if ("SPRING".equalsIgnoreCase(txMode)) {
            executeInSpringTransaction(componentId, txManagerName, task);
        } else {
            task.run();
        }
    }

    /**
     * 在 Spring 事务中执行服务组
     */
    protected void executeInSpringTransaction(String componentId,
                                              String txManagerName,
                                              Runnable task) throws Exception {
        PlatformTransactionManager txManager = resolveTransactionManager(txManagerName);
        if (txManager == null) {
            log.warn("[ServiceGroupNodeComponent] 未找到事务管理器, 以非事务方式执行: componentId={}, txManagerName={}",
                    componentId, txManagerName);
            task.run();
            return;
        }

        TransactionTemplate template = new TransactionTemplate(txManager);
        template.execute(status -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    /**
     * 解析事务管理器
     */
    protected PlatformTransactionManager resolveTransactionManager(String txManagerName) {
        if (applicationContext == null) {
            return null;
        }

        try {
            if (txManagerName != null && !txManagerName.isBlank()) {
                if (applicationContext.containsBean(txManagerName)) {
                    return applicationContext.getBean(txManagerName, PlatformTransactionManager.class);
                }
            }
        } catch (Exception e) {
            log.warn("[ServiceGroupNodeComponent] 根据名称获取事务管理器失败: name={}, error={}",
                    txManagerName, e.getMessage());
        }

        try {
            return applicationContext.getBean(PlatformTransactionManager.class);
        } catch (Exception e) {
            log.warn("[ServiceGroupNodeComponent] 按类型获取默认事务管理器失败: error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行单个子节点
     */
    protected void executeChildNode(String componentId,
                                    NodeDefinition child,
                                    FlowDefinition flowDefinition,
                                    FlowContext flowContext) throws Exception {
        String childType = child.getType();
        FlowExecutor executor = executorRegistry.get(childType);
        if (executor == null) {
            throw new IllegalStateException("Executor not found for child node type: " + childType);
        }

        NodeExecutionContext executionContext = new NodeExecutionContext(
                flowDefinition,
                child,
                flowContext
        );

        log.info("[ServiceGroupNodeComponent] 执行服务组子节点: groupId={}, childId={}, childType={}",
                componentId, child.getId(), childType);
        executor.execute(executionContext);
    }
}

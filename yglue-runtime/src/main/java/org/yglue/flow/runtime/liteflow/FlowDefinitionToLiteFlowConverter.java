package org.yglue.flow.runtime.liteflow;

import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.liteflow.adapter.ServiceNodeComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将 FlowDefinition 转换为 LiteFlow 规则
 * <p>
 * 负责将流程定义转换为 LiteFlow EL 表达式并注册到 LiteFlow 引擎中。
 * </p>
 * 
 * @author yglue
 * @since 1.0
 */
public class FlowDefinitionToLiteFlowConverter {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionToLiteFlowConverter.class);

    /** 已注册的链映射，key 为链名称，value 为 EL 表达式 */
    private final Map<String, String> registeredChains = new ConcurrentHashMap<>();

    /**
     * 构造函数
     */
    public FlowDefinitionToLiteFlowConverter() {
    }

    /**
     * 将 FlowDefinition 转换为 LiteFlow 规则并注册
     * 
     * @param definition 流程定义
     * @return LiteFlow chain 名称
     */
    public String convertAndRegister(FlowDefinition definition) {
        String chainName = definition.getId();
        
        // 如果已经注册过，直接返回
        if (registeredChains.containsKey(chainName)) {
            return chainName;
        }
        
        // 先注册所有节点到动态组件注册表
        registerNodesToDynamicComponent(definition);
        
        // 构建 LiteFlow EL 表达式
        String elExpression = buildElExpression(definition);
        
        log.info("[FlowDefinitionToLiteFlowConverter] 转换流程定义: chainName={}, elExpression={}", 
                chainName, elExpression);
        
        // 注册到 LiteFlow
        try {
            LiteFlowChainELBuilder.createChain()
                    .setChainId(chainName)
                    .setEL(elExpression)
                    .build();
            
            registeredChains.put(chainName, elExpression);
            log.info("[FlowDefinitionToLiteFlowConverter] 成功注册 LiteFlow 规则: chainName={}", chainName);
        } catch (Exception e) {
            log.error("[FlowDefinitionToLiteFlowConverter] 注册 LiteFlow 规则失败: chainName={}", 
                    chainName, e);
            throw new RuntimeException("Failed to register LiteFlow chain: " + chainName, e);
        }
        
        return chainName;
    }
    
    /**
     * 将所有节点注册到动态组件注册表
     */
    private void registerNodesToDynamicComponent(FlowDefinition definition) {
        List<NodeDefinition> nodes = definition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        
        for (NodeDefinition node : nodes) {
            String componentId = getComponentId(node);
            // 注册节点信息到服务组件
            ServiceNodeComponent.registerComponent(componentId, node, definition);
            log.debug("[FlowDefinitionToLiteFlowConverter] 注册节点到动态组件: componentId={}, nodeType={}, nodeId={}", 
                    componentId, node.getType(), node.getId());
        }
    }

    /**
     * 构建 LiteFlow EL 表达式
     * 将节点序列转换为 THEN 表达式，处理分支和条件
     */
    private String buildElExpression(FlowDefinition definition) {
        List<NodeDefinition> nodes = definition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        
        List<String> expressions = new ArrayList<>();
        for (NodeDefinition node : nodes) {
            String nodeExpr = convertNodeToEl(node);
            if (nodeExpr != null && !nodeExpr.isEmpty()) {
                expressions.add(nodeExpr);
            }
        }
        
        if (expressions.isEmpty()) {
            return "";
        }
        
        // 使用 THEN 连接所有节点
        return String.join(" THEN ", expressions);
    }

    /**
     * 将节点转换为 LiteFlow EL 表达式
     */
    private String convertNodeToEl(NodeDefinition node) {
        // 根据节点类型生成对应的 LiteFlow 组件调用
        String componentId = getComponentId(node);
        String componentRef = getComponentRef(componentId);
        
        String type = node.getType();
        
        // 处理条件节点
        if ("if".equalsIgnoreCase(type)) {
            return buildIfExpression(node, componentRef);
        }
        
        // 处理分支节点
        if ("branch".equalsIgnoreCase(type)) {
            return buildBranchExpression(node, componentRef);
        }
        
        // 普通节点，使用 dynamicNode 组件
        return componentRef;
    }

    /**
     * 获取 LiteFlow 组件 ID
     * 根据节点类型和配置生成唯一的组件标识
     * 
     * 注意：这里返回的组件 ID 会作为参数传递给 dynamicNode 组件
     * 实际在 EL 表达式中使用固定的组件 ID "dynamicNode"
     */
    private String getComponentId(NodeDefinition node) {
        String type = node.getType();
        String nodeId = node.getId();
        
        // 为每种节点类型生成对应的组件 ID
        // 格式：{type}_{nodeId}，例如：service_node1, transformer_node2, rest_node3
        return type.toLowerCase() + "_" + nodeId;
    }
    
    /**
     * 获取 LiteFlow EL 表达式中的组件引用
     * 使用固定的 serviceNode 组件，通过 tag 传递实际的组件 ID
     */
    private String getComponentRef(String componentId) {
        // 使用 serviceNode 组件，并通过 tag 传递组件 ID
        // 格式：serviceNode.tag("componentId")
        return String.format("serviceNode.tag(\"%s\")", componentId);
    }

    /**
     * 构建 IF 条件表达式
     */
    private String buildIfExpression(NodeDefinition node, String componentId) {
        Map<String, Object> config = node.getConfig();
        Object condition = config.get("condition");
        
        if (condition == null) {
            log.warn("[FlowDefinitionToLiteFlowConverter] IF 节点缺少 condition: nodeId={}", node.getId());
            return componentId;
        }
        
        // 获取 then 和 else 分支
        @SuppressWarnings("unchecked")
        Map<String, List<NodeDefinition>> blocks = 
                (Map<String, List<NodeDefinition>>) config.get("blocks");
        
        if (blocks == null) {
            return componentId;
        }
        
        List<NodeDefinition> thenNodes = blocks.get("then");
        List<NodeDefinition> elseNodes = blocks.get("else");
        
        StringBuilder expr = new StringBuilder();
        expr.append("IF(").append(componentId).append(", ");
        expr.append(convertCondition(condition)).append(")");
        
        // THEN 分支
        if (thenNodes != null && !thenNodes.isEmpty()) {
            List<String> thenExprs = new ArrayList<>();
            for (NodeDefinition thenNode : thenNodes) {
                String thenExpr = convertNodeToEl(thenNode);
                if (thenExpr != null && !thenExpr.isEmpty()) {
                    thenExprs.add(thenExpr);
                }
            }
            if (!thenExprs.isEmpty()) {
                expr.append(".THEN(").append(String.join(", ", thenExprs)).append(")");
            }
        }
        
        // ELSE 分支
        if (elseNodes != null && !elseNodes.isEmpty()) {
            List<String> elseExprs = new ArrayList<>();
            for (NodeDefinition elseNode : elseNodes) {
                String elseExpr = convertNodeToEl(elseNode);
                if (elseExpr != null && !elseExpr.isEmpty()) {
                    elseExprs.add(elseExpr);
                }
            }
            if (!elseExprs.isEmpty()) {
                expr.append(".ELSE(").append(String.join(", ", elseExprs)).append(")");
            }
        }
        
        return expr.toString();
    }

    /**
     * 构建分支表达式
     */
    private String buildBranchExpression(NodeDefinition node, String componentId) {
        // 分支节点在 LiteFlow 中可以使用 SWITCH 或并行执行
        // 这里简化处理，使用 SWITCH
        return "SWITCH(" + componentId + ")";
    }

    /**
     * 转换条件表达式
     * 将配置中的条件转换为 LiteFlow EL 表达式
     */
    private String convertCondition(Object condition) {
        if (condition == null) {
            return "true";
        }
        
        if (condition instanceof String) {
            return (String) condition;
        }
        
        // 如果是对象，尝试转换为字符串表达式
        return String.valueOf(condition);
    }

    /**
     * 取消注册规则
     */
    public void unregister(String ruleId) {
        registeredChains.remove(ruleId);
        // TODO: 从 LiteFlow 中移除规则
        // 注意：这里不清除 ServiceNodeComponent 的注册，因为可能有多个流程共享节点
    }

    /**
     * 清除所有注册的规则
     */
    public void clearAll() {
        registeredChains.clear();
        ServiceNodeComponent.clearAll();
        // TODO: 清除 LiteFlow 中的所有规则
    }
}

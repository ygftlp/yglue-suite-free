package org.yglue.flow.runtime.liteflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.liteflow.model.LiteFlowExpression;
import org.yglue.flow.runtime.liteflow.model.LiteFlowRuleModel;

import java.util.*;

/**
 * FlowDefinition 到 LiteFlowRuleModel 转换器
 * 将流程定义转换为结构化的 LiteFlow 规则模型
 * 
 * @author yglue
 * @since 1.0
 */
public class FlowDefinitionToRuleModelConverter {
    
    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionToRuleModelConverter.class);
    
    /**
     * 将 FlowDefinition 转换为 LiteFlowRuleModel
     */
    public LiteFlowRuleModel convert(FlowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("FlowDefinition cannot be null");
        }
        
        String chainName = definition.getId();
        if (chainName == null || chainName.isEmpty()) {
            throw new IllegalArgumentException("Chain name cannot be null or empty");
        }
        
        LiteFlowRuleModel model = new LiteFlowRuleModel();
        model.setChainName(chainName);
        
        // 构建表达式
        LiteFlowExpression rootExpression = buildExpression(definition);
        model.setRootExpression(rootExpression);
        
        // 收集所有节点组件
        List<NodeDefinition> nodes = definition.getNodes();
        if (nodes != null) {
            for (NodeDefinition node : nodes) {
                if (node != null && node.getId() != null) {
                    // 根据节点类型获取对应的组件名称
                    String componentName = getComponentNameByNodeType(node.getType(), node);
                    
                    LiteFlowRuleModel.NodeComponent component = new LiteFlowRuleModel.NodeComponent(
                            node.getId(),
                            node.getType(),
                            componentName,  // 设置组件名称
                            node,
                            definition
                    );
                    model.addNodeComponent(component);
                }
            }
        }
        
        return model;
    }
    
    /**
     * 构建表达式
     */
    private LiteFlowExpression buildExpression(FlowDefinition definition) {
        List<NodeDefinition> nodes = definition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return new LiteFlowExpression.ThenExpression();
        }
        
        // 获取 edges 信息
        List<Map<String, Object>> edges = definition.getEdges();
        
        // 如果没有 edges，按顺序用 THEN 连接所有节点
        if (edges == null || edges.isEmpty()) {
            return buildSequentialExpression(nodes);
        }
        
        // 构建节点映射
        Map<String, NodeDefinition> nodeMap = new LinkedHashMap<>();
        for (NodeDefinition node : nodes) {
            if (node != null && node.getId() != null) {
                nodeMap.put(node.getId(), node);
            }
        }
        
        // 构建邻接表和入度表
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        
        // 初始化
        for (String nodeId : nodeMap.keySet()) {
            adjacency.put(nodeId, new ArrayList<>());
            indegree.put(nodeId, 0);
        }
        
        // 构建图结构
        for (Map<String, Object> edge : edges) {
            String source = (String) edge.get("source");
            String target = (String) edge.get("target");
            
            if (source != null && target != null && nodeMap.containsKey(source) && nodeMap.containsKey(target)) {
                adjacency.get(source).add(target);
                indegree.put(target, indegree.get(target) + 1);
            }
        }
        
        // 找到入口节点（入度为0或节点类型为entry）
        List<String> entryNodes = new ArrayList<>();
        for (String nodeId : nodeMap.keySet()) {
            NodeDefinition node = nodeMap.get(nodeId);
            if (indegree.get(nodeId) == 0 || "entry".equalsIgnoreCase(node.getType())) {
                entryNodes.add(nodeId);
            }
        }
        
        // 如果没有找到入口节点，使用第一个节点
        if (entryNodes.isEmpty() && !nodes.isEmpty()) {
            entryNodes.add(nodes.get(0).getId());
        }
        
        // 如果还是没有节点，返回空表达式
        if (entryNodes.isEmpty()) {
            return new LiteFlowExpression.ThenExpression();
        }
        
        // 从入口节点开始构建表达式
        if (entryNodes.size() == 1) {
            return buildExpressionFromNode(entryNodes.get(0), nodeMap, adjacency, new HashSet<>());
        } else {
            // 多个入口节点，使用 WHEN 并行执行
            LiteFlowExpression.WhenExpression whenExpr = new LiteFlowExpression.WhenExpression();
            for (String entryNode : entryNodes) {
                LiteFlowExpression expr = buildExpressionFromNode(entryNode, nodeMap, adjacency, new HashSet<>());
                if (expr != null) {
                    whenExpr.add(expr);
                }
            }
            return whenExpr.getExpressions().isEmpty() ? new LiteFlowExpression.ThenExpression() : whenExpr;
        }
    }
    
    /**
     * 按顺序构建表达式（没有 edges 时使用）
     */
    private LiteFlowExpression buildSequentialExpression(List<NodeDefinition> nodes) {
        LiteFlowExpression.ThenExpression thenExpr = new LiteFlowExpression.ThenExpression();
        for (NodeDefinition node : nodes) {
            LiteFlowExpression nodeExpr = convertNodeToExpression(node);
            if (nodeExpr != null) {
                thenExpr.add(nodeExpr);
            }
        }
        return thenExpr;
    }
    
    /**
     * 从指定节点开始递归构建表达式
     */
    private LiteFlowExpression buildExpressionFromNode(
            String nodeId,
            Map<String, NodeDefinition> nodeMap,
            Map<String, List<String>> adjacency,
            Set<String> visited) {
        
        if (nodeId == null || visited.contains(nodeId)) {
            return null;
        }
        
        NodeDefinition node = nodeMap.get(nodeId);
        if (node == null) {
            return null;
        }
        
        visited.add(nodeId);
        
        String nodeType = node.getType();
        List<String> nextNodes = adjacency.get(nodeId);
        
        // 跳过 entry 和 exit 节点
        if ("entry".equalsIgnoreCase(nodeType)) {
            if (nextNodes != null && !nextNodes.isEmpty()) {
                return buildExpressionFromNode(nextNodes.get(0), nodeMap, adjacency, visited);
            }
            return null;
        }
        
        if ("exit".equalsIgnoreCase(nodeType)) {
            return null;
        }
        
        // 构建当前节点的表达式
        LiteFlowExpression currentExpr = convertNodeToExpression(node);
        
        // 如果没有后续节点，直接返回当前节点
        if (nextNodes == null || nextNodes.isEmpty()) {
            return currentExpr;
        }
        
        // 处理分支节点
        if ("branch".equalsIgnoreCase(nodeType)) {
            return buildBranchExpression(node, nodeMap, adjacency, nextNodes, visited);
        }
        
        // 处理多个后续节点（并行或分支）
        if (nextNodes.size() > 1) {
            LiteFlowExpression.WhenExpression whenExpr = new LiteFlowExpression.WhenExpression();
            for (String nextNode : nextNodes) {
                LiteFlowExpression nextExpr = buildExpressionFromNode(nextNode, nodeMap, adjacency, new HashSet<>(visited));
                if (nextExpr != null) {
                    whenExpr.add(nextExpr);
                }
            }
            
            if (whenExpr.getExpressions().isEmpty()) {
                return currentExpr;
            } else {
                LiteFlowExpression.ThenExpression thenExpr = new LiteFlowExpression.ThenExpression();
                thenExpr.add(currentExpr);
                thenExpr.add(whenExpr);
                return thenExpr;
            }
        }
        
        // 单个后续节点，顺序执行
        LiteFlowExpression nextExpr = buildExpressionFromNode(nextNodes.get(0), nodeMap, adjacency, visited);
        if (nextExpr == null) {
            return currentExpr;
        } else {
            LiteFlowExpression.ThenExpression thenExpr = new LiteFlowExpression.ThenExpression();
            thenExpr.add(currentExpr);
            thenExpr.add(nextExpr);
            return thenExpr;
        }
    }
    
    /**
     * 构建分支表达式（基于 edges）
     */
    private LiteFlowExpression buildBranchExpression(
            NodeDefinition branchNode,
            Map<String, NodeDefinition> nodeMap,
            Map<String, List<String>> adjacency,
            List<String> nextNodes,
            Set<String> visited) {
        
        // 如果有多个分支，使用 SWITCH
        if (nextNodes.size() > 2) {
            LiteFlowExpression.SwitchExpression switchExpr = new LiteFlowExpression.SwitchExpression("serviceNode");
            for (String nextNode : nextNodes) {
                LiteFlowExpression expr = buildExpressionFromNode(nextNode, nodeMap, adjacency, new HashSet<>(visited));
                if (expr != null) {
                    switchExpr.addBranch(expr);
                }
            }
            return switchExpr;
        }
        
        // 两个分支，使用 IF-ELSE
        if (nextNodes.size() == 2) {
            LiteFlowExpression thenExpr = buildExpressionFromNode(nextNodes.get(0), nodeMap, adjacency, new HashSet<>(visited));
            LiteFlowExpression elseExpr = buildExpressionFromNode(nextNodes.get(1), nodeMap, adjacency, new HashSet<>(visited));
            
            // 将 thenExpr 包装到 THEN 中
            LiteFlowExpression.ThenExpression thenWrapper = new LiteFlowExpression.ThenExpression();
            if (thenExpr != null) {
                thenWrapper.add(thenExpr);
            }
            
            // 将 elseExpr 包装到 ELSE（实际上也是 ThenExpression）中
            LiteFlowExpression.ThenExpression elseWrapper = null;
            if (elseExpr != null) {
                elseWrapper = new LiteFlowExpression.ThenExpression();
                elseWrapper.add(elseExpr);
            }
            
            return new LiteFlowExpression.IfExpression("serviceNode", thenWrapper, elseWrapper);
        }
        
        // 单个分支，直接顺序执行
        if (nextNodes.size() == 1) {
            LiteFlowExpression nextExpr = buildExpressionFromNode(nextNodes.get(0), nodeMap, adjacency, new HashSet<>(visited));
            if (nextExpr == null) {
                return convertNodeToExpression(branchNode);
            } else {
                LiteFlowExpression.ThenExpression thenExpr = new LiteFlowExpression.ThenExpression();
                thenExpr.add(convertNodeToExpression(branchNode));
                thenExpr.add(nextExpr);
                return thenExpr;
            }
        }
        
        return convertNodeToExpression(branchNode);
    }
    
    /**
     * 将节点转换为表达式
     */
    private LiteFlowExpression convertNodeToExpression(NodeDefinition node) {
        if (node == null || node.getId() == null) {
            return null;
        }
        
        String nodeType = node.getType();
        
        // 跳过 entry 和 exit 节点
        if ("entry".equalsIgnoreCase(nodeType) || "exit".equalsIgnoreCase(nodeType)) {
            return null;
        }
        
        // 根据节点类型选择对应的组件
        String componentName = getComponentNameByNodeType(nodeType, node);
        return new LiteFlowExpression.NodeExpression(node.getId(),componentName);
    }
    
    /**
     * 根据节点类型获取对应的 LiteFlow 组件名称
     */
    private String getComponentNameByNodeType(String nodeType, NodeDefinition node) {
        if (nodeType == null) {
            return "serviceNode";
        }
        
        // 根据节点类型映射到对应的组件
        switch (nodeType.toLowerCase()) {
            case "service":
                // 服务节点使用 ServiceNodeComponent
                return "serviceNode";
            case "transformer":
                // 转换器节点使用 TransformerNodeComponent（如果有的话）
                // 目前也使用 serviceNode，因为 ServiceNodeComponent 能处理所有类型
                return "serviceNode";
            case "branch":
                // 分支节点使用 serviceNode（条件判断逻辑在 ServiceNodeComponent 中处理）
                return "serviceNode";
            case "transaction":
                // 事务节点使用 serviceNode
                return "serviceNode";
            case "rest":
                // REST 调用节点使用 serviceNode
                return "serviceNode";
            default:
                // 默认使用 serviceNode
                return "serviceNode";
        }
    }
}

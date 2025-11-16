package org.yglue.flow.runtime.core.executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.NodeExecutor;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分支节点执行器
 * 用于处理条件分支节点（branch node）
 * 
 * 分支节点的执行逻辑：
 * 1. 获取从该节点出发的所有 edges
 * 2. 评估每个 edge 上的条件表达式（如果有）
 * 3. 选择第一个满足条件的 edge，执行对应的目标节点
 * 4. 如果没有 edge 满足条件，执行 fallback 分支（如果有）
 * 
 * 节点配置格式：
 * {
 *   "type": "branch",
 *   "expression": "条件表达式（可选，用于默认条件）",
 *   "fallbackNote": "回退说明（可选）"
 * }
 * 
 * Edge 配置格式（在 FlowDefinition 的 edges 中）：
 * {
 *   "source": "分支节点ID",
 *   "target": "目标节点ID",
 *   "data": {
 *     "expression": "条件表达式（SpEL 表达式）",
 *     "label": "分支标签"
 *   }
 * }
 */
public class BranchNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(BranchNodeExecutor.class);

    @Override
    public Object execute(NodeExecutionContext context) {
        String nodeId = context.getNode().getId();
        log.info("[BranchNodeExecutor] 开始执行分支节点: nodeId={}", nodeId);
        
        Map<String, Object> config = context.getNode().getConfig();
        String defaultExpression = config != null ? (String) config.get("expression") : null;
        log.debug("[BranchNodeExecutor] 分支节点配置: nodeId={}, defaultExpression={}", nodeId, defaultExpression);
        
        // 获取流程定义中的 edges
        FlowDefinition flow = context.getFlow();
        List<Map<String, Object>> edges = flow.getEdges();
        if (edges == null || edges.isEmpty()) {
            log.warn("[BranchNodeExecutor] 流程中没有 edges，无法执行分支: nodeId={}", nodeId);
            return false;
        }
        
        // 查找从当前分支节点出发的所有 edges
        List<Map<String, Object>> outgoingEdges = new ArrayList<>();
        for (Map<String, Object> edge : edges) {
            String source = (String) edge.get("source");
            if (nodeId.equals(source)) {
                outgoingEdges.add(edge);
            }
        }
        
        log.debug("[BranchNodeExecutor] 找到 {} 个出口分支: nodeId={}", outgoingEdges.size(), nodeId);
        
        if (outgoingEdges.isEmpty()) {
            log.warn("[BranchNodeExecutor] 分支节点没有出口 edges: nodeId={}", nodeId);
            return false;
        }
        
        // 评估每个 edge 的条件表达式，选择第一个满足条件的
        for (Map<String, Object> edge : outgoingEdges) {
            String targetNodeId = (String) edge.get("target");
            log.debug("[BranchNodeExecutor] 评估分支: nodeId={}, targetNodeId={}", nodeId, targetNodeId);
            
            // 获取 edge 上的条件表达式
            Object edgeData = edge.get("data");
            String expression = null;
            if (edgeData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> edgeDataMap = (Map<String, Object>) edgeData;
                expression = (String) edgeDataMap.get("expression");
            }
            
            // 如果没有 edge 上的表达式，使用默认表达式
            if (expression == null || expression.isBlank()) {
                expression = defaultExpression;
            }
            
            // 如果没有表达式，默认走这个分支（无条件分支）
            boolean conditionMet = true;
            if (expression != null && !expression.isBlank()) {
                try {
                    conditionMet = ExpressionEvaluator.evaluateBoolean(expression, context.getContext());
                    log.debug("[BranchNodeExecutor] 条件评估结果: nodeId={}, expression={}, result={}", 
                        nodeId, expression, conditionMet);
                } catch (Exception e) {
                    log.warn("[BranchNodeExecutor] 条件表达式评估失败: nodeId={}, expression={}, error={}", 
                        nodeId, expression, e.getMessage());
                    // 评估失败，跳过这个分支
                    continue;
                }
            }
            
            // 如果条件满足，执行目标节点
            if (conditionMet) {
                log.info("[BranchNodeExecutor] 选择分支: nodeId={}, targetNodeId={}, expression={}", 
                    nodeId, targetNodeId, expression);
                
                // 查找目标节点并执行
                NodeDefinition targetNode = findNodeById(flow, targetNodeId);
                if (targetNode != null) {
                    context.executeChildren(List.of(targetNode));
                    return true;
                } else {
                    log.warn("[BranchNodeExecutor] 目标节点不存在: nodeId={}, targetNodeId={}", 
                        nodeId, targetNodeId);
                }
            }
        }
        
        // 如果没有分支满足条件，检查是否有 fallback
        String fallbackNote = config != null ? (String) config.get("fallbackNote") : null;
        if (fallbackNote != null && !fallbackNote.isBlank()) {
            log.info("[BranchNodeExecutor] 所有分支条件都不满足，使用 fallback: nodeId={}, fallbackNote={}", 
                nodeId, fallbackNote);
        } else {
            log.warn("[BranchNodeExecutor] 所有分支条件都不满足，且没有 fallback: nodeId={}", nodeId);
        }
        
        return false;
    }
    
    /**
     * 根据节点ID查找节点定义
     */
    private NodeDefinition findNodeById(FlowDefinition flow, String nodeId) {
        if (flow == null || flow.getNodes() == null) {
            return null;
        }
        for (NodeDefinition node : flow.getNodes()) {
            if (nodeId.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }
}





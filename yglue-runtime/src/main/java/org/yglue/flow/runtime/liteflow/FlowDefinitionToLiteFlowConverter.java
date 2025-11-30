package org.yglue.flow.runtime.liteflow;

import com.yomahub.liteflow.builder.LiteFlowNodeBuilder;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.enums.NodeTypeEnum;
import com.yomahub.liteflow.flow.FlowBus;
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
     * 生成LiteFlow规则内容预览
     * 
     * @param definition 流程定义
     * @return LiteFlow规则内容
     */
    public String generateRulePreview(FlowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("FlowDefinition cannot be null");
        }
        
        String chainName = definition.getId();
        if (chainName == null || chainName.isEmpty()) {
            throw new IllegalArgumentException("Chain name cannot be null or empty");
        }
        
        // 构建 LiteFlow EL 表达式
        String elExpression = buildElExpression(definition);
        
        // 确保EL表达式不为空
        if (elExpression == null || elExpression.trim().isEmpty()) {
            elExpression = "THEN()"; // 默认空表达式
        }
        
        // 生成规则预览内容
        StringBuilder preview = new StringBuilder();
        preview.append("# LiteFlow Rule Preview\n");
        preview.append("# Generated: ").append(new java.util.Date()).append("\n\n");
        preview.append("[LiteFlow EL Expression]\n");
        preview.append("chain(\"").append(chainName).append("\") = ").append(elExpression).append("\n\n");
        
        // 添加节点信息
        preview.append("[Nodes]\n");
        List<NodeDefinition> nodes = definition.getNodes();
        if (nodes != null) {
            for (NodeDefinition node : nodes) {
                if (node != null) {
                    String componentId = node.getId();
                    preview.append("- ").append(componentId)
                           .append(" (").append(node.getType() != null ? node.getType() : "unknown").append(")")
                           .append(" -> ").append(getComponentRef(componentId)).append("\n");
                }
            }
        }
        
        return preview.toString();
    }
    
    /**
     * 生成LiteFlow XML规则格式
     * 
     * @param definition 流程定义
     * @return XML格式的规则内容
     */
    public String generateXmlRule(FlowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("FlowDefinition cannot be null");
        }
        
        String chainName = definition.getId();
        if (chainName == null || chainName.isEmpty()) {
            throw new IllegalArgumentException("Chain name cannot be null or empty");
        }
        
        // 构建 LiteFlow EL 表达式
        String elExpression = buildElExpression(definition);
        
        // 确保EL表达式不为空
        if (elExpression == null || elExpression.trim().isEmpty()) {
            elExpression = "THEN()"; // 默认空表达式
        }
        
        // 生成XML格式规则
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<flow>\n");
        xml.append("    <chain name=\"").append(chainName).append("\">").append(elExpression).append("</chain>\n");
        xml.append("</flow>");
        
        return xml.toString();
    }
    
    /**
     * 将 FlowDefinition 转换为 LiteFlow 规则并注册
     * 
     * @param definition 流程定义
     * @return LiteFlow chain 名称
     */
    public String convertAndRegister(FlowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("FlowDefinition cannot be null");
        }
        
        String chainName = definition.getId();
        if (chainName == null || chainName.isEmpty()) {
            throw new IllegalArgumentException("Chain name cannot be null or empty");
        }
        
        // 如果已经注册过，直接返回
        if (registeredChains.containsKey(chainName)) {
            log.debug("[FlowDefinitionToLiteFlowConverter] 链已注册，直接返回: chainName={}", chainName);
            return chainName;
        }
        
        // 先创建和注册所有节点
        registerNodes(definition);
        
        // 先注册所有节点到动态组件注册表
        registerNodesToDynamicComponent(definition);
        
        // 生成规则预览并输出到日志
        try {
            String rulePreview = generateRulePreview(definition);
            log.info("[FlowDefinitionToLiteFlowConverter] LiteFlow规则预览:\n{}", rulePreview);
        } catch (Exception e) {
            log.warn("[FlowDefinitionToLiteFlowConverter] 生成规则预览失败", e);
        }
        
        // 生成XML规则内容并输出到日志
        try {
            String xmlRule = generateXmlRule(definition);
            log.info("[FlowDefinitionToLiteFlowConverter] LiteFlow XML规则内容:\n{}", xmlRule);
        } catch (Exception e) {
            log.warn("[FlowDefinitionToLiteFlowConverter] 生成XML规则失败", e);
        }
        
        // 构建 LiteFlow EL 表达式
        String elExpression = buildElExpression(definition);
        
        // 确保EL表达式不为空
        if (elExpression == null || elExpression.trim().isEmpty()) {
            elExpression = "THEN()"; // 默认空表达式
        }
        
        // 输出生成的规则内容用于调试
        log.info("[FlowDefinitionToLiteFlowConverter] 生成的LiteFlow规则内容: chainName={}, elExpression={}", 
                chainName, elExpression);
        
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
            log.error("[FlowDefinitionToLiteFlowConverter] 注册 LiteFlow 规则失败: chainName={}, elExpression={}", 
                    chainName, elExpression, e);
            throw new RuntimeException("Failed to register LiteFlow chain: " + chainName + ", EL: " + elExpression, e);
        }
        
        return chainName;
    }
    
    /**
     * 创建和注册所有节点
     */
    private void registerNodes(FlowDefinition definition) {
        // 检查是否已经注册过serviceNode组件
        if (FlowBus.containNode("serviceNode")) {
            log.debug("[FlowDefinitionToLiteFlowConverter] serviceNode组件已存在，无需重复注册");
            return;
        }
        
        try {
            // 创建并注册serviceNode组件
            LiteFlowNodeBuilder.createNode()
                    .setId("serviceNode")
                    .setName("Service Node")
                    .setType(NodeTypeEnum.COMMON)
                    .setClazz(ServiceNodeComponent.class)
                    .build();
            
            log.debug("[FlowDefinitionToLiteFlowConverter] 创建并注册serviceNode组件");
        } catch (Exception e) {
            log.warn("[FlowDefinitionToLiteFlowConverter] 创建并注册serviceNode组件时出错", e);
        }
    }
    
    /**
     * 将所有节点注册到动态组件注册表
     */
    private void registerNodesToDynamicComponent(FlowDefinition definition) {
        List<NodeDefinition> nodes = definition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            log.debug("[FlowDefinitionToLiteFlowConverter] 没有节点需要注册");
            return;
        }
        
        for (NodeDefinition node : nodes) {
            // 确保节点不为空
            if (node == null) {
                log.warn("[FlowDefinitionToLiteFlowConverter] 跳过空节点注册");
                continue;
            }
            
            try {
                // 使用节点ID作为组件ID，LiteFlow会根据组件ID找到对应的组件
                String componentId = node.getId();
                // 确保componentId不为空
                if (componentId == null || componentId.isEmpty()) {
                    log.warn("[FlowDefinitionToLiteFlowConverter] 跳过组件ID为空的节点注册: nodeType={}, nodeId={}", 
                            node.getType(), node.getId());
                    continue;
                }
                
                // 注册节点信息到服务组件
                // 注意：这里使用节点ID作为key注册，与ServiceNodeComponent.process()方法中通过getNodeId()获取的key保持一致
                ServiceNodeComponent.registerComponent(componentId, node, definition);
                
                log.debug("[FlowDefinitionToLiteFlowConverter] 注册节点到动态组件: componentId={}, nodeType={}, nodeId={}", 
                        componentId, node.getType(), node.getId());
            } catch (Exception e) {
                log.warn("[FlowDefinitionToLiteFlowConverter] 注册节点到动态组件时出错: nodeType={}, nodeId={}", 
                        node.getType(), node.getId(), e);
            }
        }
    }

    /**
     * 构建 LiteFlow EL 表达式
     * 将节点序列转换为 THEN 表达式，处理分支和条件
     */
    private String buildElExpression(FlowDefinition definition) {
        List<NodeDefinition> nodes = definition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            // 返回一个空的chain表达式而不是空字符串
            return "THEN()";
        }
        
        List<String> expressions = new ArrayList<>();
        for (NodeDefinition node : nodes) {
            String nodeExpr = convertNodeToEl(node);
            // 确保节点表达式不为空且有效
            if (nodeExpr != null && !nodeExpr.trim().isEmpty()) {
                expressions.add(nodeExpr);
            }
        }
        
        if (expressions.isEmpty()) {
            // 返回一个空的chain表达式而不是空字符串
            return "THEN()";
        }
        
        // 使用 THEN 连接所有节点
        if (expressions.size() == 1) {
            // 对于单个节点，返回完整的THEN表达式
            return "THEN(" + expressions.get(0) + ")";
        } else {
            return "THEN(" + String.join(", ", expressions) + ")";
        }
    }

    /**
     * 将节点转换为 LiteFlow EL 表达式
     */
    private String convertNodeToEl(NodeDefinition node) {
        // 检查节点是否有效
        if (node == null) {
            log.warn("[FlowDefinitionToLiteFlowConverter] 节点为空，跳过转换");
            return null;
        }
        
        // 使用节点ID作为组件ID，LiteFlow会根据组件ID找到对应的组件
        String componentId = node.getId();
        // 确保componentId不为空
        if (componentId == null || componentId.isEmpty()) {
            log.warn("[FlowDefinitionToLiteFlowConverter] 组件ID为空，跳过节点转换: nodeId={}", 
                    node.getId());
            return null;
        }
        
        String type = node.getType();
        
        // 处理条件节点
        if ("if".equalsIgnoreCase(type)) {
            return buildIfExpression(node, componentId);
        }
        
        // 处理分支节点
        if ("branch".equalsIgnoreCase(type)) {
            return buildBranchExpression(node, componentId);
        }
        
        // 普通节点，直接使用serviceNode组件
        // ServiceNodeComponent会通过componentRegistry和getNodeId()获取实际的节点信息
        return "serviceNode";
    }

    /**
     * 获取 LiteFlow EL 表达式中的组件引用
     * 直接使用serviceNode组件
     */
    private String getComponentRef(String componentId) {
        // 确保 componentId 不为空
        if (componentId == null || componentId.isEmpty()) {
            throw new IllegalArgumentException("Component ID cannot be null or empty");
        }
        
        // 直接使用serviceNode组件
        // ServiceNodeComponent会通过componentRegistry和getNodeId()获取实际的节点信息
        return "serviceNode";
    }

    /**
     * 构建 IF 条件表达式
     */
    private String buildIfExpression(NodeDefinition node, String componentId) {
        // 获取条件配置
        Object conditionObj = node.getConfigValue("condition");
        String condition = convertCondition(conditionObj);
        
        // 获取 then 和 else 分支
        Map<String, Object> blocks = (Map<String, Object>) node.getConfigValue("blocks");
        
        StringBuilder expr = new StringBuilder();
        expr.append("IF(").append(condition);
        
        boolean hasThen = false;
        boolean hasElse = false;
        
        if (blocks != null) {
            // THEN 分支
            List<NodeDefinition> thenNodes = (List<NodeDefinition>) blocks.get("then");
            if (thenNodes != null && !thenNodes.isEmpty()) {
                List<String> thenExprs = new ArrayList<>();
                for (NodeDefinition thenNode : thenNodes) {
                    String thenExpr = convertNodeToEl(thenNode);
                    if (thenExpr != null && !thenExpr.trim().isEmpty()) {
                        thenExprs.add(thenExpr);
                    }
                }
                if (!thenExprs.isEmpty()) {
                    expr.append(", THEN(");
                    expr.append(String.join(", ", thenExprs));
                    expr.append(")");
                    hasThen = true;
                }
            }
            
            // ELSE 分支
            List<NodeDefinition> elseNodes = (List<NodeDefinition>) blocks.get("else");
            if (elseNodes != null && !elseNodes.isEmpty()) {
                List<String> elseExprs = new ArrayList<>();
                for (NodeDefinition elseNode : elseNodes) {
                    String elseExpr = convertNodeToEl(elseNode);
                    if (elseExpr != null && !elseExpr.trim().isEmpty()) {
                        elseExprs.add(elseExpr);
                    }
                }
                if (!elseExprs.isEmpty()) {
                    expr.append(", ELSE(");
                    expr.append(String.join(", ", elseExprs));
                    expr.append(")");
                    hasElse = true;
                }
            }
        }
        
        // 如果没有then和else分支，添加空的then分支以避免语法错误
        if (!hasThen && !hasElse) {
            expr.append(", THEN()");
        } else if (!hasThen) {
            expr.append(", THEN()");
        }
        
        expr.append(")");
        return expr.toString();
    }
    
    /**
     * 构建分支表达式
     */
    private String buildBranchExpression(NodeDefinition node, String componentId) {
        // 分支节点在 LiteFlow 中可以使用 SWITCH 或并行执行
        // 这里简化处理，使用 SWITCH，直接使用serviceNode组件
        // ServiceNodeComponent会通过componentRegistry和getNodeId()获取实际的节点信息
        return "SWITCH(serviceNode).TO()";
    }

    /**
     * 转换条件表达式
     * 将配置中的条件转换为 LiteFlow EL 表达式
     */
    private String convertCondition(Object condition) {
        if (condition == null) {
            log.debug("[FlowDefinitionToLiteFlowConverter] 条件为空，使用默认值 true");
            return "true";
        }
        
        String conditionStr = condition.toString().trim();
        if (conditionStr.isEmpty()) {
            log.debug("[FlowDefinitionToLiteFlowConverter] 条件字符串为空，使用默认值 true");
            return "true";
        }
        
        // 如果是字符串，直接返回
        if (condition instanceof String) {
            return "\"" + conditionStr + "\"";
        }
        
        // 如果是对象，尝试转换为字符串表达式
        return conditionStr;
    }

    /**
     * 取消注册规则
     */
    public void unregister(String ruleId) {
        registeredChains.remove(ruleId);
        // 从 LiteFlow 中移除规则
        try {
            // 注意：LiteFlow可能没有直接的API来移除已注册的chain
            // 我们只能从本地缓存中移除，下次注册时会重新创建
            log.debug("[FlowDefinitionToLiteFlowConverter] 从本地缓存中移除规则: ruleId={}", ruleId);
        } catch (Exception e) {
            log.warn("[FlowDefinitionToLiteFlowConverter] 从 LiteFlow 中移除规则时出错: ruleId={}", ruleId, e);
        }
        // 注意：这里不清除 ServiceNodeComponent 的注册，因为可能有多个流程共享节点
    }

    /**
     * 清除所有注册的规则
     */
    public void clearAll() {
        registeredChains.clear();
        ServiceNodeComponent.clearAll();
        // 清除 LiteFlow 中的所有规则
        try {
            // 注意：LiteFlow可能没有直接的API来清除所有已注册的chain
            // 我们只能从本地缓存中清除，下次注册时会重新创建
            log.debug("[FlowDefinitionToLiteFlowConverter] 清除本地缓存中的所有规则");
        } catch (Exception e) {
            log.warn("[FlowDefinitionToLiteFlowConverter] 清除 LiteFlow 中的所有规则时出错", e);
        }
    }
}
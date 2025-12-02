package org.yglue.flow.runtime.liteflow.model;

import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * LiteFlow 规则模型
 * 作为 FlowDefinition 和 LiteFlow EL 表达式之间的中间表示
 * 
 * @author yglue
 * @since 1.0
 */
public class LiteFlowRuleModel {
    
    /** 链名称 */
    private String chainName;
    
    /** 根表达式 */
    private LiteFlowExpression rootExpression;
    
    /** 节点组件列表 */
    private List<NodeComponent> nodeComponents = new ArrayList<>();
    
    public LiteFlowRuleModel() {
    }
    
    public LiteFlowRuleModel(String chainName, LiteFlowExpression rootExpression) {
        this.chainName = chainName;
        this.rootExpression = rootExpression;
    }
    
    public String getChainName() {
        return chainName;
    }
    
    public void setChainName(String chainName) {
        this.chainName = chainName;
    }
    
    public LiteFlowExpression getRootExpression() {
        return rootExpression;
    }
    
    public void setRootExpression(LiteFlowExpression rootExpression) {
        this.rootExpression = rootExpression;
    }
    
    public List<NodeComponent> getNodeComponents() {
        return nodeComponents;
    }
    
    public void setNodeComponents(List<NodeComponent> nodeComponents) {
        this.nodeComponents = nodeComponents;
    }
    
    public void addNodeComponent(NodeComponent component) {
        this.nodeComponents.add(component);
    }
    
    /**
     * 生成 LiteFlow EL 表达式字符串
     */
    public String toElExpression() {
        if (rootExpression == null) {
            return "THEN()";
        }
        return rootExpression.toElString();
    }
    
    /**
     * 生成 XML 格式规则
     */
    public String toXmlRule() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<flow>\n");
        xml.append("    <chain name=\"").append(chainName).append("\">");
        xml.append(toElExpression());
        xml.append("</chain>\n");
        xml.append("</flow>");
        return xml.toString();
    }
    
    @Override
    public String toString() {
        return "LiteFlowRuleModel{" +
                "chainName='" + chainName + '\'' +
                ", rootExpression=" + rootExpression +
                ", nodeComponents=" + nodeComponents.size() +
                '}';
    }
    
    /**
     * 节点组件
     */
    public static class NodeComponent {
        private String componentId;
        private String nodeType;
        private String componentName;  // LiteFlow 组件名称
        private NodeDefinition nodeDefinition;
        private FlowDefinition flowDefinition;
        
        public NodeComponent() {
        }
        
        public NodeComponent(String componentId, String nodeType, NodeDefinition nodeDefinition, FlowDefinition flowDefinition) {
            this.componentId = componentId;
            this.nodeType = nodeType;
            this.nodeDefinition = nodeDefinition;
            this.flowDefinition = flowDefinition;
            // 默认组件名为 serviceNode
            this.componentName = "serviceNode";
        }
        
        public NodeComponent(String componentId, String nodeType, String componentName, NodeDefinition nodeDefinition, FlowDefinition flowDefinition) {
            this.componentId = componentId;
            this.nodeType = nodeType;
            this.componentName = componentName;
            this.nodeDefinition = nodeDefinition;
            this.flowDefinition = flowDefinition;
        }
        
        public String getComponentId() {
            return componentId;
        }
        
        public void setComponentId(String componentId) {
            this.componentId = componentId;
        }
        
        public String getNodeType() {
            return nodeType;
        }
        
        public void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }
        
        public String getComponentName() {
            return componentName;
        }
        
        public void setComponentName(String componentName) {
            this.componentName = componentName;
        }
        
        public NodeDefinition getNodeDefinition() {
            return nodeDefinition;
        }
        
        public void setNodeDefinition(NodeDefinition nodeDefinition) {
            this.nodeDefinition = nodeDefinition;
        }
        
        public FlowDefinition getFlowDefinition() {
            return flowDefinition;
        }
        
        public void setFlowDefinition(FlowDefinition flowDefinition) {
            this.flowDefinition = flowDefinition;
        }
        
        @Override
        public String toString() {
            return "NodeComponent{" +
                    "componentId='" + componentId + '\'' +
                    ", nodeType='" + nodeType + '\'' +
                    ", componentName='" + componentName + '\'' +
                    '}';
        }
    }
}

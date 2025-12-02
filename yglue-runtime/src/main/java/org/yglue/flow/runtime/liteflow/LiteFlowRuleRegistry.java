package org.yglue.flow.runtime.liteflow;

import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.liteflow.adapter.FlowNodeComponent;
import org.yglue.flow.runtime.liteflow.model.LiteFlowRuleModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LiteFlow 规则注册器
 * 负责将 LiteFlowRuleModel 注册到 LiteFlow 引擎
 * 
 * @author yglue
 * @since 1.0
 */
public class LiteFlowRuleRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(LiteFlowRuleRegistry.class);
    
    /** 已注册的链映射，key 为链名称，value 为 EL 表达式 */
    private final Map<String, String> registeredChains = new ConcurrentHashMap<>();
    
    /**
     * 注册规则模型到 LiteFlow
     * 
     * @param model 规则模型
     * @return 链名称
     */
    public String register(LiteFlowRuleModel model) {
        if (model == null) {
            throw new IllegalArgumentException("LiteFlowRuleModel cannot be null");
        }
        
        String chainName = model.getChainName();
        if (chainName == null || chainName.isEmpty()) {
            throw new IllegalArgumentException("Chain name cannot be null or empty");
        }
        
        // 如果已经注册过，直接返回
        if (registeredChains.containsKey(chainName)) {
            log.debug("[LiteFlowRuleRegistry] 链已注册，直接返回: chainName={}", chainName);
            return chainName;
        }
        
        // 注册所有节点组件
        registerNodeComponents(model);
        
        // 生成 EL 表达式
        String elExpression = model.toElExpression();
        
        // 确保EL表达式不为空
        if (elExpression == null || elExpression.trim().isEmpty()) {
            elExpression = "THEN()";
        }
        
        // 输出规则预览
        logRulePreview(model, elExpression);
        
        // 注册到 LiteFlow
        try {
            LiteFlowChainELBuilder.createChain()
                    .setChainId(chainName)
                    .setEL(elExpression)
                    .build();
            
            registeredChains.put(chainName, elExpression);
            log.info("[LiteFlowRuleRegistry] 成功注册 LiteFlow 规则: chainName={}", chainName);
        } catch (Exception e) {
            log.error("[LiteFlowRuleRegistry] 注册 LiteFlow 规则失败: chainName={}, elExpression={}", 
                    chainName, elExpression, e);
            throw new RuntimeException("Failed to register LiteFlow chain: " + chainName + ", EL: " + elExpression, e);
        }
        
        return chainName;
    }
    
    /**
     * 注册所有节点组件
     */
    private void registerNodeComponents(LiteFlowRuleModel model) {
        for (LiteFlowRuleModel.NodeComponent component : model.getNodeComponents()) {
            try {
                String componentId = component.getComponentId();
                if (componentId == null || componentId.isEmpty()) {
                    log.warn("[LiteFlowRuleRegistry] 跳过组件ID为空的节点注册: nodeType={}", 
                            component.getNodeType());
                    continue;
                }
                
                String componentName = component.getComponentName();
                if (componentName == null || componentName.isEmpty()) {
                    componentName = "serviceNode";  // 默认使用 serviceNode
                }
                
                // 所有节点组件统一注册到 FlowNodeComponent基类
                // 这样 ServiceNodeComponent、GroovyNodeComponent 等所有子类都可以共享注册表
                FlowNodeComponent.registerComponent(
                        componentId, 
                        component.getNodeDefinition(), 
                        component.getFlowDefinition()
                );
                
                log.debug("[LiteFlowRuleRegistry] 注册节点组件: componentId={}, nodeType={}, componentName={}", 
                        componentId, component.getNodeType(), componentName);
                
            } catch (Exception e) {
                log.warn("[LiteFlowRuleRegistry] 注册节点到动态组件时出错: componentId={}, nodeType={}", 
                        component.getComponentId(), component.getNodeType(), e);
            }
        }
    }
    
    /**
     * 输出规则预览到日志
     */
    private void logRulePreview(LiteFlowRuleModel model, String elExpression) {
        try {
            StringBuilder preview = new StringBuilder();
            preview.append("# LiteFlow Rule Preview\n");
            preview.append("# Generated: ").append(new java.util.Date()).append("\n\n");
            preview.append("[LiteFlow EL Expression]\n");
            preview.append("chain(\"").append(model.getChainName()).append("\") = ").append(elExpression).append("\n\n");
            
            preview.append("[Node Components]\n");
            for (LiteFlowRuleModel.NodeComponent component : model.getNodeComponents()) {
                preview.append("- ").append(component.getComponentId())
                       .append(" (").append(component.getNodeType()).append(")")
                       .append(" -> ").append(component.getComponentName()).append("\n");
            }
            
            log.info("[LiteFlowRuleRegistry] LiteFlow规则预览:\n{}", preview);
            
            // 输出 XML 格式
            String xmlRule = model.toXmlRule();
            log.info("[LiteFlowRuleRegistry] LiteFlow XML规则内容:\n{}", xmlRule);
            
        } catch (Exception e) {
            log.warn("[LiteFlowRuleRegistry] 生成规则预览失败", e);
        }
    }
    
    /**
     * 取消注册规则
     */
    public void unregister(String chainName) {
        registeredChains.remove(chainName);
        log.debug("[LiteFlowRuleRegistry] 从本地缓存中移除规则: chainName={}", chainName);
    }
    
    /**
     * 清除所有注册的规则
     */
    public void clearAll() {
        registeredChains.clear();
        FlowNodeComponent.clearAll();
        log.debug("[LiteFlowRuleRegistry] 清除本地缓存中的所有规则");
    }
    
    /**
     * 检查链是否已注册
     */
    public boolean isRegistered(String chainName) {
        return registeredChains.containsKey(chainName);
    }
    
    /**
     * 获取已注册链的 EL 表达式
     */
    public String getElExpression(String chainName) {
        return registeredChains.get(chainName);
    }
}

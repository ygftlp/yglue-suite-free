package org.yglue.flow.runtime.liteflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.liteflow.model.LiteFlowRuleModel;

/**
 * 将 FlowDefinition 转换为 LiteFlow 规则
 * <p>
 * 负责将流程定义转换为 LiteFlow 规则模型，然后注册到 LiteFlow 引擎中。
 * 采用分层架构：FlowDefinition → LiteFlowRuleModel → 注册到LiteFlow
 * </p>
 * 
 * @author yglue
 * @since 1.0
 */
public class FlowDefinitionToLiteFlowConverter {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionToLiteFlowConverter.class);

    private final FlowDefinitionToRuleModelConverter modelConverter;
    private final LiteFlowRuleRegistry ruleRegistry;

    /**
     * 构造函数
     */
    public FlowDefinitionToLiteFlowConverter() {
        this.modelConverter = new FlowDefinitionToRuleModelConverter();
        this.ruleRegistry = new LiteFlowRuleRegistry();
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
        
        // 转换为规则模型
        LiteFlowRuleModel model = modelConverter.convert(definition);
        
        // 生成 XML 规则
        return model.toXmlRule();
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
        if (ruleRegistry.isRegistered(chainName)) {
            log.debug("[FlowDefinitionToLiteFlowConverter] 链已注册，直接返回: chainName={}", chainName);
            return chainName;
        }
        
        // 步骤 1：转换为规则模型
        log.info("[FlowDefinitionToLiteFlowConverter] 步骤 1: 将 FlowDefinition 转换为 LiteFlowRuleModel");
        LiteFlowRuleModel model = modelConverter.convert(definition);
        log.info("[FlowDefinitionToLiteFlowConverter] 转换完成: {}", model);
        
        // 步骤 2：注册到 LiteFlow
        log.info("[FlowDefinitionToLiteFlowConverter] 步骤 2: 将 LiteFlowRuleModel 注册到 LiteFlow 引擎");
        String registeredChainName = ruleRegistry.register(model);
        log.info("[FlowDefinitionToLiteFlowConverter] 注册完成: chainName={}", registeredChainName);
        
        return registeredChainName;
    }
    
    /**
     * 取消注册规则
     */
    public void unregister(String ruleId) {
        ruleRegistry.unregister(ruleId);
    }

    /**
     * 清除所有注册的规则
     */
    public void clearAll() {
        ruleRegistry.clearAll();
    }
}

package org.yglue.flow.runtime.core.engine.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.yglue.flow.runtime.core.definition.FlowNode;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.engine.NodeInterceptor;
import org.yglue.flow.runtime.core.engine.NodeInterceptorChain;
import org.yglue.flow.runtime.core.engine.DefaultNodeInterceptorChain;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.Map;

/**
 * 事务拦截器
 * 用于解析节点配置中的事务策略，并包裹执行逻辑
 */
public class TransactionInterceptor implements NodeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TransactionInterceptor.class);

    private final PlatformTransactionManager transactionManager;

    public TransactionInterceptor(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object intercept(FlowContext context, FlowNode unused, NodeInterceptorChain chain) throws Exception {
        NodeDefinition node = ((DefaultNodeInterceptorChain) chain).getNodeDefinition();
        Map<String, Object> config = node.getConfig();
        Object txModeObj = config != null ? config.get("txMode") : null;

        String txMode = "NONE";
        if (txModeObj instanceof String) {
            txMode = (String) txModeObj;
        } else if (txModeObj != null) {
            txMode = txModeObj.toString();
        }

        if ("NONE".equalsIgnoreCase(txMode) || transactionManager == null) {
            return chain.proceed();
        }

        log.debug("[TransactionInterceptor] 开启事务模式 {} 节点: {}", txMode, node.getId());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        // 可以根据 txMode 配置传播属性，比如 REQUIRES_NEW
        if ("REQUIRES_NEW".equalsIgnoreCase(txMode)) {
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        } else {
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        }

        return transactionTemplate.execute(status -> {
            try {
                return chain.proceed();
            } catch (Exception e) {
                // 回滚
                status.setRollbackOnly();
                log.error("[TransactionInterceptor] 事务节点执行异常，触发事务回滚: {}", node.getId(), e);
                throw new RuntimeException("Transaction execution failed", e);
            }
        });
    }
}

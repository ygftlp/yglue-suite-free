package org.yglue.flow.runtime.core.engine.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.engine.NodeInterceptor;
import org.yglue.flow.runtime.core.engine.NodeInterceptorChain;

import java.util.Map;

public class TransactionInterceptor implements NodeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TransactionInterceptor.class);

    private final PlatformTransactionManager transactionManager;

    public TransactionInterceptor(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object intercept(FlowContext context, NodeDefinition node, NodeInterceptorChain chain) throws Exception {
        Map<String, Object> config = node.getConfig();
        Object txModeObj = config != null ? config.get("txMode") : null;

        String txMode = "NONE";
        if (txModeObj instanceof String mode) {
            txMode = mode;
        } else if (txModeObj != null) {
            txMode = txModeObj.toString();
        }

        if ("NONE".equalsIgnoreCase(txMode) || transactionManager == null) {
            return chain.proceed();
        }

        log.debug("[TransactionInterceptor] enabling transaction mode {} for node {}", txMode, node.getId());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        if ("REQUIRES_NEW".equalsIgnoreCase(txMode)) {
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        } else {
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        }

        return transactionTemplate.execute(status -> {
            try {
                return chain.proceed();
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("[TransactionInterceptor] transaction execution failed for node {}", node.getId(), e);
                throw new RuntimeException("Transaction execution failed", e);
            }
        });
    }
}

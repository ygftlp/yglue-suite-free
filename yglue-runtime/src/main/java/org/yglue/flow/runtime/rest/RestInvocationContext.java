package org.yglue.flow.runtime.rest;

import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.Map;

public class RestInvocationContext {
    private final NodeDefinition node;
    private final FlowContext context;
    private final Map<String, Object> config;
    private final ApplicationContext applicationContext;

    public RestInvocationContext(NodeDefinition node,
                                 FlowContext context,
                                 Map<String, Object> config,
                                 ApplicationContext applicationContext) {
        this.node = node;
        this.context = context;
        this.config = config;
        this.applicationContext = applicationContext;
    }

    public NodeDefinition getNode() {
        return node;
    }

    public FlowContext getContext() {
        return context;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}

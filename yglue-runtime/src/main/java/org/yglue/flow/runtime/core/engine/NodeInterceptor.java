package org.yglue.flow.runtime.core.engine;

import org.yglue.flow.runtime.core.definition.NodeDefinition;

public interface NodeInterceptor {

    Object intercept(FlowContext context, NodeDefinition node, NodeInterceptorChain chain) throws Exception;
}

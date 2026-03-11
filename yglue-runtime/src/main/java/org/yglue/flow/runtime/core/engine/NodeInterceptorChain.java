package org.yglue.flow.runtime.core.engine;

import org.yglue.flow.runtime.core.definition.NodeDefinition;

public interface NodeInterceptorChain {

    FlowContext getContext();

    NodeDefinition getNode();

    Object proceed() throws Exception;
}

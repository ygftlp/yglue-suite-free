package org.yglue.flow.runtime.core.executors;

import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.rest.RestInvocationContext;
import org.yglue.flow.runtime.rest.RestInvocationRegistry;

public class RestNodeExecutor implements FlowExecutor {

    private final RestInvocationRegistry registry;
    private final ApplicationContext applicationContext;

    public RestNodeExecutor(RestInvocationRegistry registry,
                            ApplicationContext applicationContext) {
        this.registry = registry;
        this.applicationContext = applicationContext;
    }

    @Override
    public Object execute(NodeExecutionContext context) throws Exception {
        RestInvocationContext invocationContext = new RestInvocationContext(
                context.getNode(), context.getContext(), context.getNode().getConfig(), applicationContext);
        Object result = registry.invoke(invocationContext);
        Object alias = context.getNode().getConfig().get("as");
        if (alias != null) {
            context.getContext().set(String.valueOf(alias), result);
        }
        return result;
    }
}

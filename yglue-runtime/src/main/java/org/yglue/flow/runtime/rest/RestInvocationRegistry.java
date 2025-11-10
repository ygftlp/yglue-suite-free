package org.yglue.flow.runtime.rest;

import java.util.ArrayList;
import java.util.List;

public class RestInvocationRegistry {

    private final List<RestInvocationStrategy> strategies = new ArrayList<>();

    public RestInvocationRegistry register(RestInvocationStrategy strategy) {
        strategies.add(strategy);
        return this;
    }

    public Object invoke(RestInvocationContext context) throws Exception {
        for (RestInvocationStrategy strategy : strategies) {
            if (strategy.supports(context)) {
                return strategy.invoke(context);
            }
        }
        throw new IllegalArgumentException("No RestInvocationStrategy found for node " + context.getNode().getId());
    }
}

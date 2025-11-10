package org.yglue.flow.runtime.rest;

public interface RestInvocationStrategy {
    boolean supports(RestInvocationContext context);

    Object invoke(RestInvocationContext context) throws Exception;
}

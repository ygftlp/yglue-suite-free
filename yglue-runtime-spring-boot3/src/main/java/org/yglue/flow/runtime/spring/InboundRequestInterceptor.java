package org.yglue.flow.runtime.spring;

import java.util.Map;

interface InboundRequestInterceptor {

    String code();

    void apply(InboundRequestContext context, Map<String, Object> config);
}


package org.yglue.flow.runtime.spring;

import java.util.Map;

/**
 * Extension point for business applications to adjust request payloads before a
 * flow starts.
 */
public interface InboundRequestInterceptor {

    String code();

    void apply(InboundRequestContext context, Map<String, Object> config);
}

package org.yglue.flow.runtime.business;

import org.junit.jupiter.api.Test;
import org.yglue.flow.runtime.spring.InboundInterceptorException;
import org.yglue.flow.runtime.spring.InboundRequestContext;
import org.yglue.flow.runtime.spring.InboundRequestInterceptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InboundRequestInterceptorAccessTest {

    @Test
    void businessProjectCanImplementInboundInterceptorContract() {
        InboundRequestInterceptor interceptor = new CustomBusinessInboundInterceptor();

        assertEquals("businessAudit", interceptor.code());
    }

    private static final class CustomBusinessInboundInterceptor implements InboundRequestInterceptor {

        @Override
        public String code() {
            return "businessAudit";
        }

        @Override
        public void apply(InboundRequestContext context, Map<String, Object> config) {
            context.put("request.audit.enabled", true);

            if (Boolean.TRUE.equals(config.get("block"))) {
                throw InboundInterceptorException.badRequest("blocked by business interceptor");
            }
        }
    }
}

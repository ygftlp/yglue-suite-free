package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.yglue.flow.runtime.core.FlowExecutionResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowHttpResponseWriterTest {

    private final FlowHttpResponseWriter writer = new FlowHttpResponseWriter(new ObjectMapper());

    @Test
    void writesScalarResultAsPlainText() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FlowExecutionResult result = new FlowExecutionResult("rule_1", Map.of(), "ok");

        writer.writeFlowResult(response, result, null);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("{\"errorCode\":1,\"data\":\"ok\"}", response.getContentAsString());
    }

    @Test
    void writesResponseEntityPayload() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FlowExecutionResult result = new FlowExecutionResult(
                "rule_2",
                Map.of(),
                Map.of(
                        "statusCode", 202,
                        "headers", Map.of("X-Trace", List.of("a", "b")),
                        "body", Map.of("accepted", true)));

        writer.writeFlowResult(response, result, null);

        assertEquals(202, response.getStatus());
        assertEquals(List.of("a", "b"), response.getHeaders("X-Trace"));
        assertTrue(response.getContentAsString().contains("\"accepted\":true"));
    }

    @Test
    void fallsBackToRetValueFromContextSnapshot() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FlowExecutionResult result = new FlowExecutionResult("rule_3", Map.of("ret", 42), null);

        writer.writeFlowResult(response, result, null);

        assertEquals(200, response.getStatus());
        assertEquals("{\"errorCode\":1,\"data\":42}", response.getContentAsString());
    }
}

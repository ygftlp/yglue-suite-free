package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FlowRequestInputBuilderTest {

    private final FlowRequestInputBuilder builder = new FlowRequestInputBuilder(new ObjectMapper());

    @Test
    void mergesMethodArgumentsIntoRequestPayload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/orders/42");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"ignored\":true}".getBytes(StandardCharsets.UTF_8));
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.addParameter("keep", "from-request");
        request.addHeader("X-Original", "original");

        Method method = SignatureFixture.class.getDeclaredMethod(
                "handle",
                String.class,
                Integer.class,
                String.class,
                OrderBody.class);

        Map<String, Object> flowInput = builder.buildFlowInput(
                request,
                method.getParameters(),
                new Object[]{"A-100", 7, "trace-1", new OrderBody("alice", 3)});

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) flowInput.get("request");

        assertEquals("POST", payload.get("method"));
        assertEquals("/orders/42", payload.get("uri"));
        assertEquals("A-100", section(payload, "path").get("orderId"));
        assertEquals(7, section(payload, "query").get("page"));
        assertEquals("from-request", section(payload, "query").get("keep"));
        assertEquals("trace-1", section(payload, "headers").get("X-Trace"));
        assertEquals("original", section(payload, "headers").get("X-Original"));

        Object body = payload.get("body");
        assertInstanceOf(Map.class, body);
        assertEquals("alice", ((Map<?, ?>) body).get("buyer"));
        assertEquals(3, ((Map<?, ?>) body).get("quantity"));
    }

    @Test
    void preservesAnnotatedRequestBodyListShape() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/orders/import");

        Method method = SignatureFixture.class.getDeclaredMethod("importOrders", List.class);

        Map<String, Object> flowInput = builder.buildFlowInput(
                request,
                method.getParameters(),
                new Object[]{List.of("a", "b")});

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) flowInput.get("request");

        Object body = payload.get("body");
        assertInstanceOf(List.class, body);
        assertEquals(List.of("a", "b"), body);
    }

    @Test
    void preservesAnnotatedRequestBodyScalarShape() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/orders/version");

        Method method = SignatureFixture.class.getDeclaredMethod("updateVersion", Integer.class);

        Map<String, Object> flowInput = builder.buildFlowInput(
                request,
                method.getParameters(),
                new Object[]{9});

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) flowInput.get("request");

        assertEquals(9, payload.get("body"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(Map<String, Object> payload, String key) {
        return (Map<String, Object>) payload.get(key);
    }

    private static final class SignatureFixture {
        void handle(@PathVariable("orderId") String orderId,
                @RequestParam("page") Integer page,
                @RequestHeader("X-Trace") String traceId,
                @RequestBody OrderBody body) {
        }

        void importOrders(@RequestBody List<String> ids) {
        }

        void updateVersion(@RequestBody Integer version) {
        }
    }

    private record OrderBody(String buyer, int quantity) {
    }
}

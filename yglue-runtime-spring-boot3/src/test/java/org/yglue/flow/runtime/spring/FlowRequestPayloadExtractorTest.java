package org.yglue.flow.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FlowRequestPayloadExtractorTest {

    private final FlowRequestPayloadExtractor extractor = new FlowRequestPayloadExtractor(new ObjectMapper());

    @Test
    void extractsJsonObjectBodyAsMap() throws Exception {
        MockHttpServletRequest request = jsonRequest("{\"name\":\"alice\",\"age\":21}");

        Map<String, Object> payload = extractor.extract(request, true);

        Object body = payload.get("body");
        assertInstanceOf(Map.class, body);
        assertEquals("alice", ((Map<?, ?>) body).get("name"));
        assertEquals(21, ((Map<?, ?>) body).get("age"));
    }

    @Test
    void extractsJsonArrayBodyAsList() throws Exception {
        MockHttpServletRequest request = jsonRequest("[\"a\",{\"score\":10},true]");

        Map<String, Object> payload = extractor.extract(request, true);

        Object body = payload.get("body");
        assertInstanceOf(List.class, body);
        List<?> items = (List<?>) body;
        assertEquals("a", items.get(0));
        assertEquals(10, ((Map<?, ?>) items.get(1)).get("score"));
        assertEquals(Boolean.TRUE, items.get(2));
    }

    @Test
    void extractsJsonScalarBodyWithoutStringFallback() throws Exception {
        MockHttpServletRequest request = jsonRequest("123");

        Map<String, Object> payload = extractor.extract(request, true);

        assertEquals(123, payload.get("body"));
    }

    private MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/test");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return request;
    }
}

package org.yglue.flow.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = YgflowSampleApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestEntrypointInterceptionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void orchestratedEndpointIsInterceptedWhileBaselineEndpointStillWorks() {
        ResponseEntity<String> baseline = restTemplate.getForEntity(url("/api/math/add?a=1&b=2"), String.class);
        assertEquals(200, baseline.getStatusCode().value());
        assertEquals("3", baseline.getBody());

        ResponseEntity<String> intercepted = restTemplate.getForEntity(url("/api/math/flow-add?a=1&b=2"), String.class);
        assertEquals(200, intercepted.getStatusCode().value());
        assertTrue(intercepted.getBody().contains("Processed: abc, User: {age=20}"));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}

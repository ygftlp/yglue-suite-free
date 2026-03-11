package org.yglue.flow.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.RuleEngine;
import org.yglue.flow.runtime.core.FlowExecutionResult;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = YgflowSampleApplication.class)
class SampleFlowExecutionTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void sampleMathFlowExecutes() throws IOException {
        RuleEngine engine = new RuleEngine(applicationContext);
        FlowExecutionResult result = engine.execute("sample_math_flow", Map.of());
        assertNotNull(result);
    }
}

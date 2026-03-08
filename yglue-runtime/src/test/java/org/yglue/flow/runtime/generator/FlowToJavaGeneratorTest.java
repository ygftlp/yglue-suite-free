package org.yglue.flow.runtime.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yglue.flow.runtime.core.definition.FlowDefinition;

import java.io.File;
import java.io.IOException;

public class FlowToJavaGeneratorTest {

    @Test
    public void testGenerate() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // Load sample flow
        File file = new File(
                "d:\\JavaWorkspace\\ygflow-suite\\samples\\yglue-sample-service\\src\\main\\resources\\ygflow\\rules\\sample_math_flow.json");
        FlowDefinition flow = mapper.readValue(file, FlowDefinition.class);

        FlowToJavaGenerator generator = new FlowToJavaGenerator();
        String code = generator.generate(flow, "org.example.generated", "Flow_SampleMath");

        System.out.println(code);

        // Basic assertions
        assert code.contains("package org.example.generated;");
        assert code.contains("public class Flow_SampleMath");
        assert !code.contains("@Autowired"); // Should NOT contain Autowired
        assert !code.contains("@Component"); // Should NOT contain Component
        assert code.contains("public Flow_SampleMath(org.example.TestService testService)"); // Constructor
        assert code.contains("this.testService = testService;");
        assert code.contains("testService.testB");
    }
}

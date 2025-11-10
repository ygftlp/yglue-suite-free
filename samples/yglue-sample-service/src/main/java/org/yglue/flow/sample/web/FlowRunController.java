package org.yglue.flow.sample.web;

import org.springframework.web.bind.annotation.*;
import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.RuleEngine;
import org.yglue.flow.runtime.core.FlowExecutionResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ygflow/run")
public class FlowRunController {

    private final RuleEngine engine;

    public FlowRunController(ApplicationContext ctx) {
        this.engine = new RuleEngine(ctx);
    }

    @PostMapping("/{ruleId}")
    public Map<String,Object> run(@PathVariable String ruleId, @RequestBody(required = false) Map<String,Object> input) throws IOException {
        FlowExecutionResult result = engine.execute(ruleId, input);
        Map<String, Object> response = new HashMap<>();
        response.put("ruleId", result.getRuleId());
        response.put("context", result.getContextSnapshot());
        response.put("returnValue", result.getReturnValue());
        return response;
    }
}

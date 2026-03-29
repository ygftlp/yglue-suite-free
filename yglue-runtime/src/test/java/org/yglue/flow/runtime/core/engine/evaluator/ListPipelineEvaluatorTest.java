package org.yglue.flow.runtime.core.engine.evaluator;

import org.junit.jupiter.api.Test;
import org.yglue.flow.runtime.core.engine.FlowContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListPipelineEvaluatorTest {

    @Test
    void filterSupportsItemPrefix() {
        Map<String, Object> plan = Map.of(
                "listInput", Map.of(
                        "kind", "const",
                        "constValue", List.of(
                                Map.of("code", "A", "enabled", true),
                                Map.of("code", "B", "enabled", false))),
                "listSteps", List.of(Map.of(
                        "op", "filter",
                        "exprText", "item.enabled == true")));

        Object result = ListPipelineEvaluator.evaluate(plan, new FlowContext(), Map.of());

        assertThat(result).asList().hasSize(1);
        assertThat(result).asList().first().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "A");
    }

    @Test
    void mapSupportsRequestAndTempPrefixes() {
        FlowContext context = new FlowContext();
        context.put("request", Map.of("tenantId", "tenant-a"));

        Map<String, Object> plan = Map.of(
                "listInput", Map.of(
                        "kind", "const",
                        "constValue", List.of(
                                Map.of("code", "A"),
                                Map.of("code", "B"))),
                "listSteps", List.of(Map.of(
                        "op", "map",
                        "exprText", "request.tenantId + '-' + item.code + '-' + temp.suffix")));

        Object result = ListPipelineEvaluator.evaluate(plan, context, Map.of("suffix", "ready"));

        assertThat(result).asList().containsExactly("tenant-a-A-ready", "tenant-a-B-ready");
    }
}

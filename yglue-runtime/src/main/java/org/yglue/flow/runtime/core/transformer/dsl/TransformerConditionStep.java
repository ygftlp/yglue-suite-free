package org.yglue.flow.runtime.core.transformer.dsl;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;
import java.util.Objects;

@JsonTypeName("condition")
public record TransformerConditionStep(
        String when,
        List<TransformerStep> thenSteps,
        List<TransformerStep> elseSteps
) implements TransformerStep {

    public TransformerConditionStep {
        Objects.requireNonNull(when, "when must not be null");
        thenSteps = thenSteps == null ? List.of() : List.copyOf(thenSteps);
        elseSteps = elseSteps == null ? List.of() : List.copyOf(elseSteps);
    }

    @Override
    public TransformerStepType type() {
        return TransformerStepType.CONDITIONAL;
    }
}





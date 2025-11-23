package org.yglue.flow.runtime.core.transformer.dsl;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;
import java.util.Objects;

@JsonTypeName("object")
public record TransformerObjectStep(
        String target,
        List<TransformerStep> properties
) implements TransformerStep {

    public TransformerObjectStep {
        Objects.requireNonNull(target, "target must not be null");
        properties = properties == null ? List.of() : List.copyOf(properties);
    }

    @Override
    public TransformerStepType type() {
        return TransformerStepType.OBJECT;
    }
}





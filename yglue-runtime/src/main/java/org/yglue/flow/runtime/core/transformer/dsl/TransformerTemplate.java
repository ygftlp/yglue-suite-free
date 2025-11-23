package org.yglue.flow.runtime.core.transformer.dsl;

import java.util.List;
import java.util.Objects;

public record TransformerTemplate(
        String name,
        TransformerOutputMode outputMode,
        List<TransformerStep> steps
) {

    public TransformerTemplate {
        Objects.requireNonNull(outputMode, "outputMode must not be null");
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}





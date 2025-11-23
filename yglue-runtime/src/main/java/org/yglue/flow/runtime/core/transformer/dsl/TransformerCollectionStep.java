package org.yglue.flow.runtime.core.transformer.dsl;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;
import java.util.Objects;

@JsonTypeName("collection")
public record TransformerCollectionStep(
        String target,
        String source,
        String resolverRef,
        String itemAlias,
        String filter,
        List<TransformerStep> itemSteps
) implements TransformerStep {

    public TransformerCollectionStep {
        Objects.requireNonNull(target, "target must not be null");
        if ((source == null || source.isBlank()) && (resolverRef == null || resolverRef.isBlank())) {
            throw new IllegalArgumentException("collection step requires source or resolverRef");
        }
        itemAlias = (itemAlias == null || itemAlias.isBlank()) ? "item" : itemAlias;
        itemSteps = itemSteps == null ? List.of() : List.copyOf(itemSteps);
    }

    @Override
    public TransformerStepType type() {
        return TransformerStepType.COLLECTION;
    }
}





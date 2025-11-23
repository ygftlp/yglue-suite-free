package org.yglue.flow.runtime.core.transformer.dsl;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.yglue.flow.runtime.core.param.ValueType;

import java.util.Objects;

@JsonTypeName("field")
public record TransformerFieldStep(
        String target,
        String source,
        String resolverRef,
        String expression,
        ValueType cast,
        Object defaultValue,
        boolean required
) implements TransformerStep {

    public TransformerFieldStep {
        Objects.requireNonNull(target, "target must not be null");
        if (cast == null) {
            cast = ValueType.AUTO;
        }
    }

    @Override
    public TransformerStepType type() {
        return TransformerStepType.FIELD;
    }
}





package org.yglue.flow.runtime.core.transformer.runtime;

import org.yglue.flow.runtime.core.param.ResolveError;

import java.util.Collections;
import java.util.List;

public final class TransformerExecutionResult {

    private final Object output;
    private final List<ResolveError> errors;

    public TransformerExecutionResult(Object output, List<ResolveError> errors) {
        this.output = output;
        this.errors = errors == null ? List.of() : Collections.unmodifiableList(errors);
    }

    public Object output() {
        return output;
    }

    public List<ResolveError> errors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}





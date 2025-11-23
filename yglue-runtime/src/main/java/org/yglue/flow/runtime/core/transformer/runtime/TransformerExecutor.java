package org.yglue.flow.runtime.core.transformer.runtime;

import org.yglue.flow.runtime.core.transformer.dsl.TransformerTemplate;

public interface TransformerExecutor {

    TransformerExecutionResult execute(TransformerTemplate template, TransformerExecutionContext context);
}





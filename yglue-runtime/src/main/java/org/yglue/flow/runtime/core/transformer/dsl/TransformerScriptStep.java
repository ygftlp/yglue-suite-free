package org.yglue.flow.runtime.core.transformer.dsl;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Objects;

@JsonTypeName("script")
public record TransformerScriptStep(
        String language,
        String inlineScript,
        String scriptRef
) implements TransformerStep {

    public TransformerScriptStep {
        if ((inlineScript == null || inlineScript.isBlank()) && (scriptRef == null || scriptRef.isBlank())) {
            throw new IllegalArgumentException("script step requires inlineScript or scriptRef");
        }
        language = (language == null || language.isBlank()) ? "groovy" : language;
    }

    @Override
    public TransformerStepType type() {
        return TransformerStepType.SCRIPT;
    }
}





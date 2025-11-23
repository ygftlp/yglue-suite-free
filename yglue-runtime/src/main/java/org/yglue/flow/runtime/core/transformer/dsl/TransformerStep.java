package org.yglue.flow.runtime.core.transformer.dsl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransformerFieldStep.class, name = "field"),
        @JsonSubTypes.Type(value = TransformerObjectStep.class, name = "object"),
        @JsonSubTypes.Type(value = TransformerCollectionStep.class, name = "collection"),
        @JsonSubTypes.Type(value = TransformerConditionStep.class, name = "condition"),
        @JsonSubTypes.Type(value = TransformerScriptStep.class, name = "script")
})
public interface TransformerStep {

    TransformerStepType type();
}





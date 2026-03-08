package org.yglue.flow.runtime.events;

import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;

public record FlowCompletedEvent(FlowDefinition flow, FlowContext context) {}

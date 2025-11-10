package org.yglue.flow.runtime.events;

import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;

public record FlowStartedEvent(FlowDefinition flow, FlowContext context) {}

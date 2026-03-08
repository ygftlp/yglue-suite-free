package org.yglue.flow.runtime.events;

import org.yglue.flow.runtime.core.engine.FlowContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

public record NodeErrorEvent(FlowDefinition flow,
                             NodeDefinition node,
                             FlowContext context,
                             Exception exception) {}

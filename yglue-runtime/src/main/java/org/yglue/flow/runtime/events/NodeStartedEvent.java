package org.yglue.flow.runtime.events;

import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

public record NodeStartedEvent(FlowDefinition flow,
                               NodeDefinition node,
                               FlowContext context) {}

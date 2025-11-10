package org.yglue.flow.runtime.core;

import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.List;

@FunctionalInterface
public interface ChildNodeRunner {
    void run(List<NodeDefinition> nodes);
}

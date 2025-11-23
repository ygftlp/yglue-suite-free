package org.yglue.flow.runtime.core;

import org.yglue.flow.runtime.FlowContext;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.validator.ValidationException;
import org.yglue.flow.runtime.events.EventBus;
import org.yglue.flow.runtime.events.FlowCompletedEvent;
import org.yglue.flow.runtime.events.FlowStartedEvent;
import org.yglue.flow.runtime.events.NodeErrorEvent;
import org.yglue.flow.runtime.events.NodeFinishedEvent;
import org.yglue.flow.runtime.events.NodeStartedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FlowExecutor {

    private final NodeExecutorRegistry registry;
    private final List<ExecutionInterceptor> interceptors;
    private final EventBus eventBus;

    public FlowExecutor(NodeExecutorRegistry registry,
                        List<ExecutionInterceptor> interceptors,
                        EventBus eventBus) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.interceptors = interceptors == null ? List.of() : new ArrayList<>(interceptors);
        this.eventBus = eventBus;
    }

    public FlowExecutionResult execute(FlowDefinition flowDefinition, Map<String, Object> input) {
        FlowContext context = new FlowContext();
        if (input != null) {
            context.data().putAll(input);
        }
        if (flowDefinition.getVariables() != null) {
            flowDefinition.getVariables().forEach(context::set);
        }
        publish(new FlowStartedEvent(flowDefinition, context));
        for (NodeDefinition node : flowDefinition.getNodes()) {
            runNode(flowDefinition, node, context);
        }
        publish(new FlowCompletedEvent(flowDefinition, context));
        return new FlowExecutionResult(
                flowDefinition.getId(),
                context.snapshot(),
                context.getReturnValue());
    }

    private void runNode(FlowDefinition flow, NodeDefinition node, FlowContext context) {
        NodeExecutionContext executionContext = new NodeExecutionContext(flow, node, context, children -> {
            if (children == null) {
                return;
            }
            for (NodeDefinition child : children) {
                runNode(flow, child, context);
            }
        });
        publish(new NodeStartedEvent(flow, node, context));
        context.pushNodeScope();
        for (ExecutionInterceptor interceptor : interceptors) {
            interceptor.beforeNode(executionContext);
        }
        Object result = null;
        try {
            NodeExecutor executor = registry.get(node.getType());
            result = executor.execute(executionContext);
            
            /**
             * 自动将节点返回值保存到上下文
             * 使用节点ID作为key，方便后续节点（如转换器）访问前一个节点的输出
             * 同时保存到 _lastNodeResult，方便转换器快速获取最后一个节点的输出
             */
            if (result != null) {
                context.set("_node_" + node.getId(), result);
                context.set("_lastNodeResult", result);
            }
            
            for (ExecutionInterceptor interceptor : interceptors) {
                interceptor.afterNode(executionContext, result);
            }
            publish(new NodeFinishedEvent(flow, node, context, result));
        } catch (ValidationException ve) {
            // ValidationException 需要直接抛出，不要包装，以便 FlowOrchestratedAspect 能够捕获
            for (ExecutionInterceptor interceptor : interceptors) {
                interceptor.onError(executionContext, ve);
            }
            publish(new NodeErrorEvent(flow, node, context, ve));
            throw ve;
        } catch (Exception ex) {
            for (ExecutionInterceptor interceptor : interceptors) {
                interceptor.onError(executionContext, ex);
            }
            publish(new NodeErrorEvent(flow, node, context, ex));
            throw new RuntimeException("Node execution failed: " + node.getId() + " (" + node.getType() + ")", ex);
        } finally {
            context.popNodeScope();
        }
    }

    private void publish(Object event) {
        if (eventBus != null && event != null) {
            eventBus.publish(event);
        }
    }
}

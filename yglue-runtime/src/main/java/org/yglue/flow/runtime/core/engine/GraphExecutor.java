package org.yglue.flow.runtime.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;
import org.yglue.flow.runtime.core.executors.BranchSelection;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphExecutor {

    private static final Logger log = LoggerFactory.getLogger(GraphExecutor.class);

    private final NodeExecutorRegistry executorRegistry;
    private final List<NodeInterceptor> interceptors;

    public GraphExecutor(NodeExecutorRegistry executorRegistry, List<NodeInterceptor> interceptors) {
        this.executorRegistry = executorRegistry;
        this.interceptors = interceptors != null ? interceptors : new ArrayList<>();
    }

    public FlowExecutionResult execute(FlowDefinition definition, Map<String, Object> input) {
        String ruleId = definition.getId();
        log.info("[GraphExecutor] start flow execution ruleId={}", ruleId);

        FlowContext context = new FlowContext(ruleId, input);

        try {
            List<NodeDefinition> nodes = definition.getNodes();
            List<Map<String, Object>> edges = definition.getEdges();

            if (nodes == null || nodes.isEmpty()) {
                return new FlowExecutionResult(ruleId, context.getVariables(), null);
            }

            if (edges == null || edges.isEmpty()) {
                executeSequential(definition, nodes, context);
            } else {
                executeDag(definition, context);
            }
        } catch (Exception e) {
            log.error("[GraphExecutor] flow execution failed ruleId={}", ruleId, e);
            context.setException(e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Flow execution failed: " + e.getMessage(), e);
        }

        return new FlowExecutionResult(ruleId, context.getVariables(), context.getReturnValue());
    }

    private void executeSequential(FlowDefinition definition, List<NodeDefinition> nodes, FlowContext context)
            throws Exception {
        for (NodeDefinition node : nodes) {
            executeNode(definition, node, context);
        }
    }

    private void executeDag(FlowDefinition definition, FlowContext context) throws Exception {
        DagExecutionState dagState = new DagExecutionState();
        Map<String, NodeDefinition> nodeMap = new LinkedHashMap<>();
        for (NodeDefinition node : definition.getNodes()) {
            if (node != null && node.getId() != null) {
                nodeMap.put(node.getId(), node);
            }
        }

        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();

        for (String nodeId : nodeMap.keySet()) {
            adjacency.put(nodeId, new ArrayList<>());
            indegree.put(nodeId, 0);
        }

        for (Map<String, Object> edge : definition.getEdges()) {
            String source = (String) edge.get("source");
            String target = (String) edge.get("target");
            if (source != null && target != null && nodeMap.containsKey(source) && nodeMap.containsKey(target)) {
                adjacency.get(source).add(target);
                indegree.put(target, indegree.get(target) + 1);
            }
        }

        List<String> entryNodes = new ArrayList<>();
        for (String nodeId : nodeMap.keySet()) {
            NodeDefinition node = nodeMap.get(nodeId);
            if (indegree.get(nodeId) == 0 || "entry".equalsIgnoreCase(node.getType())) {
                entryNodes.add(nodeId);
            }
        }

        if (entryNodes.isEmpty() && !nodeMap.isEmpty()) {
            entryNodes.add(nodeMap.keySet().iterator().next());
        }

        for (String entryId : entryNodes) {
            activateNode(entryId, nodeMap, adjacency, indegree, dagState);
        }

        while (!dagState.readyQueue.isEmpty()) {
            String nodeId = dagState.readyQueue.removeFirst();
            dagState.queuedNodes.remove(nodeId);
            if (dagState.completedNodes.contains(nodeId)) {
                continue;
            }

            NodeDefinition node = nodeMap.get(nodeId);
            if (node == null) {
                continue;
            }

            if ("exit".equalsIgnoreCase(node.getType())) {
                dagState.completedNodes.add(nodeId);
                continue;
            }

            Object nodeResult = null;
            if (!"entry".equalsIgnoreCase(node.getType())) {
                nodeResult = executeNode(definition, node, context);
            }

            dagState.completedNodes.add(nodeId);

            if ("branch".equalsIgnoreCase(node.getType())) {
                if (nodeResult instanceof BranchSelection selection
                        && selection.targetNodeId() != null
                        && !selection.targetNodeId().isBlank()) {
                    activateEdge(nodeId, selection.targetNodeId(), nodeMap, adjacency, indegree, dagState);
                }
            } else {
                enqueueActivatedChildren(nodeId, nodeMap, adjacency, indegree, dagState);
            }
        }
    }

    private void activateNode(String nodeId,
            Map<String, NodeDefinition> nodeMap,
            Map<String, List<String>> adjacency,
            Map<String, Integer> indegree,
            DagExecutionState dagState) {
        if (nodeId == null || !dagState.reachableNodes.add(nodeId)) {
            return;
        }
        NodeDefinition node = nodeMap.get(nodeId);
        if (node == null) {
            return;
        }
        String type = node.getType();
        if ("exit".equalsIgnoreCase(type)) {
            return;
        }

        if (!"branch".equalsIgnoreCase(type)) {
            List<String> nextNodes = adjacency.get(nodeId);
            if (nextNodes != null) {
                for (String nextNodeId : nextNodes) {
                    activateEdge(nodeId, nextNodeId, nodeMap, adjacency, indegree, dagState);
                }
            }
        }

        tryEnqueueNode(nodeId, nodeMap, indegree, dagState);
    }

    private void activateEdge(String sourceNodeId,
            String targetNodeId,
            Map<String, NodeDefinition> nodeMap,
            Map<String, List<String>> adjacency,
            Map<String, Integer> indegree,
            DagExecutionState dagState) {
        if (sourceNodeId == null || targetNodeId == null || !nodeMap.containsKey(targetNodeId)) {
            return;
        }
        dagState.activePredecessors.computeIfAbsent(targetNodeId, key -> new LinkedHashSet<>()).add(sourceNodeId);
        activateNode(targetNodeId, nodeMap, adjacency, indegree, dagState);
        tryEnqueueNode(targetNodeId, nodeMap, indegree, dagState);
    }

    private void enqueueActivatedChildren(String nodeId,
            Map<String, NodeDefinition> nodeMap,
            Map<String, List<String>> adjacency,
            Map<String, Integer> indegree,
            DagExecutionState dagState) {
        List<String> nextNodes = adjacency.get(nodeId);
        if (nextNodes == null) {
            return;
        }
        for (String nextNodeId : nextNodes) {
            tryEnqueueNode(nextNodeId, nodeMap, indegree, dagState);
        }
    }

    private void tryEnqueueNode(String nodeId,
            Map<String, NodeDefinition> nodeMap,
            Map<String, Integer> indegree,
            DagExecutionState dagState) {
        if (nodeId == null || dagState.completedNodes.contains(nodeId) || dagState.queuedNodes.contains(nodeId)) {
            return;
        }
        NodeDefinition node = nodeMap.get(nodeId);
        if (node == null || "exit".equalsIgnoreCase(node.getType())) {
            return;
        }

        Set<String> activePredecessors = dagState.activePredecessors.get(nodeId);
        if (activePredecessors == null || activePredecessors.isEmpty()) {
            if (indegree.getOrDefault(nodeId, 0) == 0 || "entry".equalsIgnoreCase(node.getType())) {
                dagState.queuedNodes.add(nodeId);
                dagState.readyQueue.addLast(nodeId);
            }
            return;
        }

        if (dagState.completedNodes.containsAll(activePredecessors)) {
            dagState.queuedNodes.add(nodeId);
            dagState.readyQueue.addLast(nodeId);
        }
    }

    private static final class DagExecutionState {
        private final Set<String> reachableNodes = new LinkedHashSet<>();
        private final Set<String> completedNodes = new LinkedHashSet<>();
        private final Set<String> queuedNodes = new LinkedHashSet<>();
        private final Map<String, Set<String>> activePredecessors = new LinkedHashMap<>();
        private final Deque<String> readyQueue = new ArrayDeque<>();
    }

    private Object executeNode(FlowDefinition definition, NodeDefinition node, FlowContext context) throws Exception {
        String type = node.getType() != null ? node.getType() : "service";
        FlowExecutor targetExecutor;
        try {
            targetExecutor = executorRegistry.get(type);
        } catch (IllegalArgumentException e) {
            log.warn("[GraphExecutor] no executor found for type {}, fallback to service", type);
            targetExecutor = executorRegistry.get("service");
        }

        log.debug("[GraphExecutor] executing node {} (type={})", node.getId(), type);

        DefaultNodeInterceptorChain chain = new DefaultNodeInterceptorChain(
                interceptors,
                definition,
                context,
                node,
                targetExecutor,
                childNode -> {
                    try {
                        executeNode(definition, childNode, context);
                    } catch (Exception e) {
                        throw new RuntimeException("Child node execution failed: " + childNode.getId(), e);
                    }
                });

        Object result = chain.proceed();

        Object asObj = node.getConfig() != null ? node.getConfig().get("as") : null;
        if (!(result instanceof BranchSelection) && asObj instanceof String alias && !alias.isBlank()) {
            context.put(alias, result);
        }
        if (!(result instanceof BranchSelection)) {
            context.setReturnValue(result);
        }
        return result;
    }
}

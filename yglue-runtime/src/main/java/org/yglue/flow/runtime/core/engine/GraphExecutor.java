package org.yglue.flow.runtime.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.runtime.core.FlowExecutionResult;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.NodeExecutorRegistry;
import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GraphExecutor {

    private static final Logger log = LoggerFactory.getLogger(GraphExecutor.class);

    private final NodeExecutorRegistry executorRegistry;
    private final List<NodeInterceptor> interceptors;
    private final ExecutorService threadPool;

    public GraphExecutor(NodeExecutorRegistry executorRegistry, List<NodeInterceptor> interceptors) {
        this.executorRegistry = executorRegistry;
        this.interceptors = interceptors != null ? interceptors : new ArrayList<>();
        this.threadPool = Executors.newCachedThreadPool();
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

    private void executeDag(FlowDefinition definition, FlowContext context) {
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

        Set<String> visited = new HashSet<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String entryId : entryNodes) {
            futures.add(executePathAsync(definition, entryId, nodeMap, adjacency, visited, context));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private CompletableFuture<Void> executePathAsync(FlowDefinition definition,
            String nodeId,
            Map<String, NodeDefinition> nodeMap,
            Map<String, List<String>> adjacency,
            Set<String> currentPathVisited,
            FlowContext context) {
        if (nodeId == null || currentPathVisited.contains(nodeId)) {
            return CompletableFuture.completedFuture(null);
        }

        NodeDefinition node = nodeMap.get(nodeId);
        if (node == null || "exit".equalsIgnoreCase(node.getType())) {
            return CompletableFuture.completedFuture(null);
        }

        currentPathVisited.add(nodeId);

        if ("entry".equalsIgnoreCase(node.getType())) {
            List<String> nextNodes = adjacency.get(nodeId);
            if (nextNodes == null || nextNodes.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return executePathAsync(definition, nextNodes.get(0), nodeMap, adjacency, currentPathVisited, context);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                executeNode(definition, node, context);
            } catch (Exception e) {
                throw new RuntimeException("Node execution failed: " + node.getId(), e);
            }
        }, threadPool).thenComposeAsync(v -> {
            List<String> nextNodes = adjacency.get(nodeId);
            if (nextNodes == null || nextNodes.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            if ("branch".equalsIgnoreCase(node.getType())) {
                Object selectedTarget = context.getReturnValue();
                if (selectedTarget instanceof String targetNodeId && !targetNodeId.isBlank()) {
                    return executePathAsync(definition, targetNodeId, nodeMap, adjacency, currentPathVisited, context);
                }
                return CompletableFuture.completedFuture(null);
            }

            if (nextNodes.size() == 1) {
                return executePathAsync(definition, nextNodes.get(0), nodeMap, adjacency, currentPathVisited, context);
            }

            List<CompletableFuture<Void>> nextFutures = new ArrayList<>();
            for (String nextNodeId : nextNodes) {
                nextFutures.add(executePathAsync(definition, nextNodeId, nodeMap, adjacency,
                        new HashSet<>(currentPathVisited), context));
            }
            return CompletableFuture.allOf(nextFutures.toArray(new CompletableFuture[0]));
        }, threadPool);
    }

    private void executeNode(FlowDefinition definition, NodeDefinition node, FlowContext context) throws Exception {
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
        if (asObj instanceof String alias && !alias.isBlank()) {
            context.put(alias, result);
        }
        context.setReturnValue(result);
    }
}

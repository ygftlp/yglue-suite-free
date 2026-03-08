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

/**
 * 原生 DAG 图执行引擎
 * 替代 LiteFlow，提供更轻量、更高性能的结构化执行能力
 */
public class GraphExecutor {

    private static final Logger log = LoggerFactory.getLogger(GraphExecutor.class);

    private final NodeExecutorRegistry executorRegistry;
    private final List<NodeInterceptor> interceptors;
    private final ExecutorService threadPool;

    public GraphExecutor(NodeExecutorRegistry executorRegistry, List<NodeInterceptor> interceptors) {
        this.executorRegistry = executorRegistry;
        this.interceptors = interceptors != null ? interceptors : new ArrayList<>();
        // TODO: 可配的线程池
        this.threadPool = Executors.newCachedThreadPool();
    }

    public FlowExecutionResult execute(FlowDefinition definition, Map<String, Object> input) {
        String ruleId = definition.getId();
        log.info("[GraphExecutor] 开始执行图流程 ruleId={}", ruleId);

        FlowContext context = new FlowContext(ruleId, input);

        try {
            List<NodeDefinition> nodes = definition.getNodes();
            List<Map<String, Object>> edges = definition.getEdges();

            if (nodes == null || nodes.isEmpty()) {
                return new FlowExecutionResult(ruleId, context.getVariables(), null);
            }

            if (edges == null || edges.isEmpty()) {
                // 退化为简单的串行执行
                executeSequential(nodes, context);
            } else {
                // DAG 执行
                executeDag(definition, context);
            }
        } catch (Exception e) {
            log.error("[GraphExecutor] 流程执行异常 ruleId={}", ruleId, e);
            context.setException(e);

            // 如果内部抛出了 RuntimeException，直接向上抛出
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Flow execution failed: " + e.getMessage(), e);
        }

        return new FlowExecutionResult(ruleId, context.getVariables(), context.getReturnValue());
    }

    /**
     * 无边模式下的兜底串行执行
     */
    private void executeSequential(List<NodeDefinition> nodes, FlowContext context) throws Exception {
        for (NodeDefinition node : nodes) {
            executeNode(node, context);
        }
    }

    /**
     * 基于边信息的 DAG 递归遍历执行
     */
    private void executeDag(FlowDefinition definition, FlowContext context) throws Exception {
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

        // 递归执行
        Set<String> visited = new HashSet<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String entryId : entryNodes) {
            futures.add(executePathAsync(entryId, nodeMap, adjacency, visited, context));
        }

        // 等待所有入口节点衍生的一条条路径跑完
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private CompletableFuture<Void> executePathAsync(String nodeId,
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

        // 如果是 entry 节点，跳过自己的逻辑，直接跑后续节点
        if ("entry".equalsIgnoreCase(node.getType())) {
            List<String> nextNodes = adjacency.get(nodeId);
            if (nextNodes == null || nextNodes.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return executePathAsync(nextNodes.get(0), nodeMap, adjacency, currentPathVisited, context);
        }

        // 执行当前节点 (可以是异步或同步执行，这里统一在当前被调度的线程池线程中执行)
        return CompletableFuture.runAsync(() -> {
            try {
                executeNode(node, context);
            } catch (Exception e) {
                throw new RuntimeException("Node execution failed: " + node.getId(), e);
            }
        }, threadPool).thenComposeAsync(v -> {
            // 当前节点执行完，去找后续节点
            List<String> nextNodes = adjacency.get(nodeId);
            if (nextNodes == null || nextNodes.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            // TODO : 如果当前节点是 branch (选择分支)，这里应该只走激活的边，暂时默认全选

            if (nextNodes.size() == 1) {
                // 串行
                return executePathAsync(nextNodes.get(0), nodeMap, adjacency, currentPathVisited, context);
            } else {
                // 并行 WHEN (Fork)
                List<CompletableFuture<Void>> nextFutures = new ArrayList<>();
                for (String nextNodeId : nextNodes) {
                    // 分支出一套独立的 visited
                    nextFutures.add(executePathAsync(nextNodeId, nodeMap, adjacency, new HashSet<>(currentPathVisited),
                            context));
                }
                return CompletableFuture.allOf(nextFutures.toArray(new CompletableFuture[0]));
            }
        }, threadPool);
    }

    private void executeNode(NodeDefinition node, FlowContext context) throws Exception {
        String type = node.getType() != null ? node.getType() : "service";
        FlowExecutor targetExecutor;
        try {
            targetExecutor = executorRegistry.get(type);
        } catch (IllegalArgumentException e) {
            log.warn("[GraphExecutor] No executor found for type {}, fallback to service", type);
            targetExecutor = executorRegistry.get("service");
        }

        log.debug("[GraphExecutor] Executing node {} (type={})", node.getId(), type);

        DefaultNodeInterceptorChain chain = new DefaultNodeInterceptorChain(
                interceptors, context, node, targetExecutor,
                childNode -> {
                    try {
                        executeNode(childNode, context);
                    } catch (Exception e) {
                        throw new RuntimeException("Child node execution failed: " + childNode.getId(), e);
                    }
                });

        Object result = chain.proceed();

        // 解析 'as' 属性，如果存在则写入 Context
        Object asObj = node.getConfig() != null ? node.getConfig().get("as") : null;
        if (asObj instanceof String && !((String) asObj).isBlank()) {
            context.put((String) asObj, result);
        }

        // 约定：最后一个被执行的节点结果或者被显式标记的结果将作为返回值
        // 通常用一个特殊的 exit 节点或者约定
        context.setReturnValue(result);
    }
}

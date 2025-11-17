package org.yglue.flow.runtime.core.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.yglue.flow.runtime.core.util.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlowLoader {

    private final ObjectMapper objectMapper;

    public FlowLoader() {
        this(new ObjectMapper());
    }

    public FlowLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FlowDefinition load(String ruleId) throws IOException {
        JsonNode root = readRule(ruleId);
        return parse(ruleId, root);
    }

    private JsonNode readRule(String ruleId) throws IOException {
        Path local = Path.of(".ygflow", "rules", ruleId + ".json");
        if (Files.exists(local)) {
            return objectMapper.readTree(local.toFile());
        }
        String cp = "/ygflow/rules/" + ruleId + ".json";
        InputStream is = FlowLoader.class.getResourceAsStream(cp);
        if (is == null) {
            throw new IOException("Rule not found: " + ruleId);
        }
        return objectMapper.readTree(is);
    }

    /**
     * 解析流程定义
     * 支持两种格式：
     * 1. 旧格式：{ "vars": {...}, "steps": [...] }
     * 2. 新格式：{ "nodes": [...], "edges": [...] } （前端保存的格式）
     * 
     * 对于新格式，会根据 edges 对节点进行拓扑排序，确保执行顺序正确
     * 
     * @param ruleId 规则ID
     * @param root JSON根节点
     * @return 流程定义
     */
    public FlowDefinition parse(String ruleId, JsonNode root) {
        Map<String, Object> vars = JsonUtils.toMap(root.path("vars"));
        List<NodeDefinition> nodes = new ArrayList<>();
        
        // 优先使用 steps 字段（旧格式）
        JsonNode steps = root.path("steps");
        if (steps.isArray() && steps.size() > 0) {
            int index = 0;
            for (JsonNode node : steps) {
                nodes.add(parseNode(ruleId, index++, node));
            }
        } else {
            // 如果没有 steps，尝试使用 nodes 字段（新格式，前端保存的格式）
            JsonNode nodesArray = root.path("nodes");
            if (nodesArray.isArray() && nodesArray.size() > 0) {
                // 先解析所有节点
                Map<String, NodeDefinition> nodeMap = new LinkedHashMap<>();
                int index = 0;
                for (JsonNode node : nodesArray) {
                    NodeDefinition nodeDef = parseNode(ruleId, index++, node);
                    nodeMap.put(nodeDef.getId(), nodeDef);
                }
                
                // 解析 edges
                List<Map<String, Object>> edges = new ArrayList<>();
                JsonNode edgesArray = root.path("edges");
                if (edgesArray.isArray() && edgesArray.size() > 0) {
                    for (JsonNode edge : edgesArray) {
                        edges.add(JsonUtils.toMap(edge));
                    }
                }
                
                // 如果有 edges，根据 edges 进行拓扑排序
                if (!edges.isEmpty()) {
                    nodes = topologicalSort(nodeMap, edgesArray);
                } else {
                    // 如果没有 edges，按原始顺序
                    nodes = new ArrayList<>(nodeMap.values());
                }
                
                // 返回包含 edges 的 FlowDefinition
                return new FlowDefinition(ruleId, vars, nodes, edges);
            }
        }
        
        return new FlowDefinition(ruleId, vars, nodes);
    }
    
    /**
     * 根据 edges 对节点进行拓扑排序
     * 确保执行顺序符合流程图的连接关系
     * 
     * @param nodeMap 节点ID到节点定义的映射
     * @param edgesArray edges JSON数组
     * @return 排序后的节点列表
     */
    private List<NodeDefinition> topologicalSort(Map<String, NodeDefinition> nodeMap, JsonNode edgesArray) {
        // 构建邻接表和入度表
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        
        // 初始化所有节点的入度为0
        for (String nodeId : nodeMap.keySet()) {
            indegree.put(nodeId, 0);
            adjacency.put(nodeId, new ArrayList<>());
        }
        
        // 处理 edges，构建邻接表和计算入度
        for (JsonNode edge : edgesArray) {
            String source = edge.has("source") ? edge.get("source").asText() : null;
            String target = edge.has("target") ? edge.get("target").asText() : null;
            
            if (source != null && target != null && nodeMap.containsKey(source) && nodeMap.containsKey(target)) {
                adjacency.get(source).add(target);
                indegree.put(target, indegree.get(target) + 1);
            }
        }
        
        // 拓扑排序：找到所有入度为0的节点（起始节点）
        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        
        List<NodeDefinition> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            NodeDefinition node = nodeMap.get(current);
            if (node != null) {
                sorted.add(node);
            }
            
            // 处理当前节点的所有后继节点
            for (String next : adjacency.get(current)) {
                int newIndegree = indegree.get(next) - 1;
                indegree.put(next, newIndegree);
                if (newIndegree == 0) {
                    queue.add(next);
                }
            }
        }
        
        // 如果还有节点没有被排序（可能是循环依赖或孤立节点），添加到末尾
        for (String nodeId : nodeMap.keySet()) {
            if (!sorted.stream().anyMatch(n -> n.getId().equals(nodeId))) {
                sorted.add(nodeMap.get(nodeId));
            }
        }
        
        return sorted;
    }

    /**
     * 解析节点定义
     * 支持前端保存的节点格式：
     * {
     *   "id": "...",
     *   "type": "service",  // 节点类型：service（本地服务）、rest（REST调用）、transformer（脚本转换）等
     *   "data": {
     *     "comp": {...},
     *     "inputs": [...],
     *     "output": {...},
     *     "label": "...",
     *     "nodeType": "service"  // 可选，如果根级别没有 type 字段，会从 data.nodeType 获取
     *   },
     *   "position": {...}
     * }
     * 
     * 对于有 "data" 字段的节点，会将 data 中的内容提取到 config 中
     */
    private NodeDefinition parseNode(String ruleId, int index, JsonNode node) {
        String nodeId = node.has("id") ? node.get("id").asText() : ruleId + "_step_" + index;
        
        // 获取节点类型：优先从根级别的 type 字段获取，如果没有则从 data.nodeType 获取
        String type = null;
        if (node.has("type")) {
            type = node.get("type").asText();
        } else if (node.has("data") && node.get("data").has("nodeType")) {
            type = node.get("data").get("nodeType").asText();
        }
        
        if (type != null && !type.isBlank()) {
            Map<String, Object> config;
            
            // 如果节点有 "data" 字段（前端保存的格式），提取 data 中的内容
            if (node.has("data") && node.get("data").isObject()) {
                // 将 data 字段中的内容作为 config
                config = JsonUtils.toMap(node.get("data"));
                // 移除 data 中的 nodeType（如果存在），因为已经提取到 type 字段了
                config.remove("nodeType");
                // 同时保留节点级别的其他配置字段（如果有的话）
                Map<String, Object> nodeLevelConfig = JsonUtils.toMap(node);
                nodeLevelConfig.remove("type");
                nodeLevelConfig.remove("data");
                nodeLevelConfig.remove("id");
                nodeLevelConfig.remove("position");
                nodeLevelConfig.remove("children");
                // 将节点级别的配置合并到 config 中（data 中的配置优先）
                if (!nodeLevelConfig.isEmpty()) {
                    Map<String, Object> merged = new LinkedHashMap<>(nodeLevelConfig);
                    merged.putAll(config);
                    config = merged;
                }
            } else {
                // 旧格式：直接使用节点本身作为 config
                config = JsonUtils.toMap(node);
                config.remove("type");
                config.remove("children");
            }
            
            if ("if".equalsIgnoreCase(type)) {
                Map<String, Object> mutable = new LinkedHashMap<>(config);
                Object condition = mutable.remove("condition");
                List<NodeDefinition> thenNodes = parseBlock(ruleId, nodeId + "_then", node.get("then"));
                List<NodeDefinition> elseNodes = parseBlock(ruleId, nodeId + "_else", node.get("else"));
                Map<String, Object> blocks = new LinkedHashMap<>();
                blocks.put("then", thenNodes);
                blocks.put("else", elseNodes);
                mutable.put("condition", condition);
                mutable.put("blocks", blocks);
                config = mutable;
            }
            List<NodeDefinition> children = parseChildren(ruleId, nodeId, node);
            return new NodeDefinition(nodeId, type, config, children);
        }
        if (node.has("log")) {
            Map<String, Object> config = Map.of("message", node.get("log").asText());
            return new NodeDefinition(nodeId, "log", config, List.of());
        }
        if (node.has("delay")) {
            Map<String, Object> config = Map.of("millis", node.get("delay").asLong());
            return new NodeDefinition(nodeId, "delay", config, List.of());
        }
        if (node.has("set")) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("target", node.get("set").asText());
            config.put("value", JsonUtils.toObject(node.get("value")));
            return new NodeDefinition(nodeId, "set", config, List.of());
        }
        if (node.has("if")) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("condition", JsonUtils.toObject(node.get("if")));
            List<NodeDefinition> thenNodes = parseBlock(ruleId, nodeId + "_then", node.get("then"));
            List<NodeDefinition> elseNodes = parseBlock(ruleId, nodeId + "_else", node.get("else"));
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("then", thenNodes);
            cfg.put("else", elseNodes);
            config.put("blocks", cfg);
            return new NodeDefinition(nodeId, "if", config, List.of());
        }
        if (node.has("call")) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("target", node.get("call").asText());
            if (node.has("args")) {
                config.put("args", JsonUtils.toList(node.get("args")));
            }
            if (node.has("as")) {
                config.put("as", node.get("as").asText());
            }
            return new NodeDefinition(nodeId, "call", config, List.of());
        }

        // fallback treat as raw object with "type" default to "custom"
        Map<String, Object> config = JsonUtils.toMap(node);
        return new NodeDefinition(nodeId, "custom", config, List.of());
    }

    private List<NodeDefinition> parseChildren(String ruleId, String nodeId, JsonNode node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return List.of();
        }
        JsonNode children = objectNode.get("children");
        if (children == null || !children.isArray()) {
            return List.of();
        }
        List<NodeDefinition> result = new ArrayList<>();
        int childIndex = 0;
        for (JsonNode child : children) {
            result.add(parseNode(ruleId, childIndex++, child));
        }
        return result;
    }

    private List<NodeDefinition> parseBlock(String ruleId, String blockId, JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<NodeDefinition> result = new ArrayList<>();
        int idx = 0;
        for (JsonNode item : node) {
            result.add(parseNode(ruleId + "_" + blockId, idx++, item));
        }
        return result;
    }
}

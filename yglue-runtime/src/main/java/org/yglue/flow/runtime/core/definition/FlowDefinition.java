package org.yglue.flow.runtime.core.definition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 流程定义
 * 包含流程的节点、边、变量和设置信息
 */
public class FlowDefinition {
    private final String id;
    private final Map<String, Object> variables;
    private final List<NodeDefinition> nodes;
    /**
     * 边的列表
     * 每个边包含 source（源节点ID）、target（目标节点ID）和 data（边的数据，可能包含条件表达式）
     */
    private final List<Map<String, Object>> edges;
    /**
     * 流程设置
     * 包含 logPolicy 等配置信息
     */
    private final Map<String, Object> settings;

    public FlowDefinition(String id,
                          Map<String, Object> variables,
                          List<NodeDefinition> nodes) {
        this(id, variables, nodes, null, null);
    }

    public FlowDefinition(String id,
                          Map<String, Object> variables,
                          List<NodeDefinition> nodes,
                          List<Map<String, Object>> edges) {
        this(id, variables, nodes, edges, null);
    }

    public FlowDefinition(String id,
                          Map<String, Object> variables,
                          List<NodeDefinition> nodes,
                          List<Map<String, Object>> edges,
                          Map<String, Object> settings) {
        this.id = id;
        this.variables = variables == null ? Map.of() : Collections.unmodifiableMap(variables);
        this.nodes = nodes == null ? List.of() : Collections.unmodifiableList(nodes);
        this.edges = edges == null ? List.of() : Collections.unmodifiableList(edges);
        this.settings = settings == null ? Map.of() : Collections.unmodifiableMap(settings);
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public List<NodeDefinition> getNodes() {
        return nodes;
    }

    /**
     * 获取边的列表
     * 用于分支节点根据条件表达式选择执行路径
     */
    public List<Map<String, Object>> getEdges() {
        return edges;
    }

    /**
     * 获取流程设置
     * 包含 logPolicy 等配置信息
     */
    public Map<String, Object> getSettings() {
        return settings;
    }
}

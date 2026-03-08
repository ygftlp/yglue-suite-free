package org.yglue.flow.runtime.generator;

import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flow to Java Code Generator
 * Converts a FlowDefinition into a plain Java class (POJO).
 */
public class FlowToJavaGenerator {

    public String generate(FlowDefinition flow, String packageName, String className) {
        StringBuilder sb = new StringBuilder();
        Set<String> imports = new HashSet<>();
        imports.add("org.yglue.flow.runtime.FlowContext");
        imports.add("java.util.Map");

        // 1. Collect imports and dependencies (Service Beans)
        Set<String> serviceBeans = new HashSet<>();
        collectDependencies(flow.getNodes(), serviceBeans);

        // 2. Generate Package
        sb.append("package ").append(packageName).append(";\n\n");

        // 3. Generate Imports
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");

        // 4. Generate Class Header
        sb.append("public class ").append(className).append(" {\n\n");

        // 5. Generate Fields (Dependencies) and Constructor
        Map<String, String> beanTypes = collectBeanTypes(flow.getNodes());

        // Fields
        for (Map.Entry<String, String> entry : beanTypes.entrySet()) {
            String beanName = entry.getKey();
            String beanType = entry.getValue();
            sb.append("    private final ").append(beanType).append(" ").append(beanName).append(";\n");
        }
        sb.append("\n");

        // Constructor
        sb.append("    public ").append(className).append("(");
        int i = 0;
        for (Map.Entry<String, String> entry : beanTypes.entrySet()) {
            String beanName = entry.getKey();
            String beanType = entry.getValue();
            if (i > 0)
                sb.append(", ");
            sb.append(beanType).append(" ").append(beanName);
            i++;
        }
        sb.append(") {\n");
        for (String beanName : beanTypes.keySet()) {
            sb.append("        this.").append(beanName).append(" = ").append(beanName).append(";\n");
        }
        sb.append("    }\n\n");

        // 6. Generate Execute Method
        sb.append("    public void execute(FlowContext ctx) {\n");

        generateNodes(sb, flow);

        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private void collectDependencies(List<NodeDefinition> nodes, Set<String> beans) {
        for (NodeDefinition node : nodes) {
            if ("service".equals(node.getType())) {
                Map<String, Object> config = node.getConfig();
                Map<String, Object> comp = (Map<String, Object>) config.get("comp");
                if (comp != null) {
                    String bean = (String) comp.get("bean");
                    if (bean != null) {
                        beans.add(bean);
                    }
                }
            }
            collectDependencies(node.getChildren(), beans);
        }
    }

    private Map<String, String> collectBeanTypes(List<NodeDefinition> nodes) {
        Map<String, String> beanTypes = new java.util.HashMap<>();
        collectBeanTypesRecursive(nodes, beanTypes);
        return beanTypes;
    }

    private void collectBeanTypesRecursive(List<NodeDefinition> nodes, Map<String, String> beanTypes) {
        for (NodeDefinition node : nodes) {
            if ("service".equals(node.getType())) {
                Map<String, Object> config = node.getConfig();
                Map<String, Object> comp = (Map<String, Object>) config.get("comp");
                if (comp != null) {
                    String bean = (String) comp.get("bean");
                    String configJsonStr = (String) comp.get("configJson");
                    // Parse configJson to get serviceClass
                    if (configJsonStr != null && configJsonStr.contains("\"serviceClass\":\"")) {
                        int start = configJsonStr.indexOf("\"serviceClass\":\"") + 16;
                        int end = configJsonStr.indexOf("\"", start);
                        String className = configJsonStr.substring(start, end);
                        beanTypes.put(bean, className);
                    } else {
                        // Fallback
                        beanTypes.put(bean, "Object");
                    }
                }
            }
            collectBeanTypesRecursive(node.getChildren(), beanTypes);
        }
    }

    private void generateNodes(StringBuilder sb, FlowDefinition flow) {
        for (NodeDefinition node : flow.getNodes()) {
            if ("service".equals(node.getType())) {
                generateServiceNode(sb, node);
            } else if ("branch".equals(node.getType())) {
                generateBranchNode(sb, node, flow.getEdges());
            }
        }
    }

    private void generateBranchNode(StringBuilder sb, NodeDefinition node, List<Map<String, Object>> edges) {
        sb.append("        // Node: ").append(node.getId()).append(" (Branch)\n");

        boolean first = true;
        for (Map<String, Object> edge : edges) {
            String source = (String) edge.get("source");
            if (node.getId().equals(source)) {
                String target = (String) edge.get("target");
                Map<String, Object> data = (Map<String, Object>) edge.get("data");
                String expression = (String) data.get("expression");

                if (first) {
                    sb.append("        if (");
                } else {
                    sb.append(" else if (");
                }

                // Embed condition script
                sb.append(expression);
                sb.append(") {\n");

                sb.append("            // Execute Node ").append(target).append("\n");
                sb.append("        }");

                first = false;
            }
        }
        if (!first) {
            sb.append("\n");
        }
    }

    private void generateServiceNode(StringBuilder sb, NodeDefinition node) {
        Map<String, Object> config = node.getConfig();
        Map<String, Object> comp = (Map<String, Object>) config.get("comp");
        String beanName = (String) comp.get("bean");
        String methodName = (String) comp.get("method");

        sb.append("        // Node: ").append(node.getId()).append("\n");

        // Inputs
        List<Map<String, Object>> inputs = (List<Map<String, Object>>) config.get("inputs");
        StringBuilder args = new StringBuilder();
        if (inputs != null) {
            for (int i = 0; i < inputs.size(); i++) {
                Map<String, Object> input = inputs.get(i);
                String name = (String) input.get("name");
                String type = (String) input.get("typeName");
                String script = (String) input.get("script");

                sb.append("        ").append(type).append(" arg").append(i).append(";\n");
                sb.append("        {\n");
                sb.append("            // Input: ").append(name).append("\n");

                sb.append("            java.util.function.Supplier<").append(type).append("> supplier = () -> {\n");
                sb.append("                ").append(script).append("\n");
                sb.append("            };\n");
                sb.append("            arg").append(i).append(" = supplier.get();\n");
                sb.append("        }\n");

                if (i > 0)
                    args.append(", ");
                args.append("arg").append(i);
            }
        }

        // Call
        sb.append("        Object result = ").append(beanName).append(".").append(methodName).append("(").append(args)
                .append(");\n");

        // Output
        String as = (String) config.get("as");
        if (as != null) {
            sb.append("        ctx.put(\"").append(as).append("\", result);\n");
        }
        sb.append("\n");
    }
}

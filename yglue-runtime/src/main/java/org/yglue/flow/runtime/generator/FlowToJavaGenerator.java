package org.yglue.flow.runtime.generator;

import org.yglue.flow.runtime.core.definition.FlowDefinition;
import org.yglue.flow.runtime.core.definition.NodeDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Generates a plain Java class from a flow definition.
 *
 * <p>This generator intentionally supports only a conservative subset of the
 * runtime model. When a node input uses multi-line scripts, the script is moved
 * into a helper method and lightly normalized so the generated source remains
 * valid Java.</p>
 */
public class FlowToJavaGenerator {

    public String generate(FlowDefinition flow, String packageName, String className) {
        TreeSet<String> imports = new TreeSet<>();
        imports.add("org.yglue.flow.runtime.FlowContext");

        StringBuilder classBody = new StringBuilder();
        StringBuilder helperMethods = new StringBuilder();
        Map<String, String> beanTypes = collectBeanTypes(flow.getNodes());

        classBody.append("public class ").append(className).append(" {\n\n");

        for (Map.Entry<String, String> entry : beanTypes.entrySet()) {
            classBody.append("    private final ")
                    .append(entry.getValue())
                    .append(" ")
                    .append(entry.getKey())
                    .append(";\n");
        }
        classBody.append("\n");

        classBody.append("    public ").append(className).append("(");
        int index = 0;
        for (Map.Entry<String, String> entry : beanTypes.entrySet()) {
            if (index++ > 0) {
                classBody.append(", ");
            }
            classBody.append(entry.getValue()).append(" ").append(entry.getKey());
        }
        classBody.append(") {\n");
        for (String beanName : beanTypes.keySet()) {
            classBody.append("        this.").append(beanName).append(" = ").append(beanName).append(";\n");
        }
        classBody.append("    }\n\n");

        classBody.append("    public void execute(FlowContext ctx) {\n");
        generateNodes(classBody, helperMethods, imports, flow);
        classBody.append("    }\n");
        if (helperMethods.length() > 0) {
            classBody.append("\n").append(helperMethods);
        }
        classBody.append("}\n");

        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        for (String imp : imports) {
            source.append("import ").append(imp).append(";\n");
        }
        source.append("\n").append(classBody);
        return source.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> collectBeanTypes(List<NodeDefinition> nodes) {
        Map<String, String> beanTypes = new HashMap<>();
        for (NodeDefinition node : nodes) {
            if ("service".equals(node.getType())) {
                Map<String, Object> config = node.getConfig();
                Map<String, Object> comp = (Map<String, Object>) config.get("comp");
                if (comp != null) {
                    String bean = asString(comp.get("bean"));
                    String configJsonStr = asString(comp.get("configJson"));
                    if (bean != null) {
                        beanTypes.put(bean, extractServiceClass(configJsonStr));
                    }
                }
            }
            beanTypes.putAll(collectBeanTypes(node.getChildren()));
        }
        return beanTypes;
    }

    private String extractServiceClass(String configJson) {
        if (configJson != null && configJson.contains("\"serviceClass\":\"")) {
            int start = configJson.indexOf("\"serviceClass\":\"") + 16;
            int end = configJson.indexOf("\"", start);
            if (end > start) {
                return configJson.substring(start, end);
            }
        }
        return "Object";
    }

    private void generateNodes(StringBuilder executeMethod,
            StringBuilder helperMethods,
            TreeSet<String> imports,
            FlowDefinition flow) {
        int helperIndex = 0;
        for (NodeDefinition node : flow.getNodes()) {
            if ("service".equals(node.getType())) {
                helperIndex = generateServiceNode(executeMethod, helperMethods, imports, node, helperIndex);
            } else if ("branch".equals(node.getType())) {
                generateBranchNode(executeMethod, node, flow.getEdges());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int generateServiceNode(StringBuilder executeMethod,
            StringBuilder helperMethods,
            TreeSet<String> imports,
            NodeDefinition node,
            int helperIndex) {
        Map<String, Object> config = node.getConfig();
        Map<String, Object> comp = (Map<String, Object>) config.get("comp");
        String beanName = asString(comp.get("bean"));
        String methodName = asString(comp.get("method"));

        executeMethod.append("        // Node: ").append(node.getId()).append("\n");

        List<Map<String, Object>> inputs = (List<Map<String, Object>>) config.get("inputs");
        StringBuilder args = new StringBuilder();
        if (inputs != null) {
            for (int i = 0; i < inputs.size(); i++) {
                Map<String, Object> input = inputs.get(i);
                String type = normalizeTypeName(asString(input.get("typeName")));
                String script = asString(input.get("script"));
                String variableName = "arg" + i;
                RenderedInput rendered = renderInput(node.getId(), variableName, type, script, helperIndex++, imports);
                executeMethod.append(rendered.statement());
                if (!rendered.helperMethod().isBlank()) {
                    helperMethods.append(rendered.helperMethod()).append("\n");
                }
                if (i > 0) {
                    args.append(", ");
                }
                args.append(variableName);
            }
        }

        executeMethod.append("        Object result = ")
                .append(beanName)
                .append(".")
                .append(methodName)
                .append("(")
                .append(args)
                .append(");\n");

        String as = asString(config.get("as"));
        if (as == null || as.isBlank()) {
            as = resolveOutputContextKey(config);
        }
        if (as != null && !as.isBlank()) {
            executeMethod.append("        ctx.set(\"").append(escapeJava(as)).append("\", result);\n");
        }
        executeMethod.append("\n");
        return helperIndex;
    }

    private void generateBranchNode(StringBuilder executeMethod,
            NodeDefinition node,
            List<Map<String, Object>> edges) {
        executeMethod.append("        // Node: ").append(node.getId()).append(" (Branch)\n");
        boolean first = true;
        for (Map<String, Object> edge : edges) {
            String source = asString(edge.get("source"));
            if (!node.getId().equals(source)) {
                continue;
            }
            String target = asString(edge.get("target"));
            executeMethod.append(first ? "        if (" : "        else if (");
            executeMethod.append("false");
            executeMethod.append(") {\n");
            executeMethod.append("            // TODO branch target: ").append(target).append("\n");
            executeMethod.append("        }\n");
            first = false;
        }
        if (first) {
            executeMethod.append("        // No outgoing branch edges\n");
        }
        executeMethod.append("\n");
    }

    private RenderedInput renderInput(String nodeId,
            String variableName,
            String type,
            String script,
            int helperIndex,
            TreeSet<String> imports) {
        String normalizedType = normalizeTypeName(type);
        String indent = "        ";
        if (script == null || script.isBlank()) {
            return new RenderedInput(indent + normalizedType + " " + variableName + " = " + defaultValue(normalizedType) + ";\n",
                    "");
        }

        String trimmed = script.trim();
        if (isInlineExpression(trimmed)) {
            if (!isSafeJavaInlineExpression(trimmed)) {
                String statement = indent + normalizedType + " " + variableName + " = " + defaultValue(normalizedType)
                        + "; // unsupported inline script: " + escapeJava(trimmed) + "\n";
                return new RenderedInput(statement, "");
            }
            return new RenderedInput(indent + normalizedType + " " + variableName + " = " + trimmed + ";\n", "");
        }

        String helperName = buildHelperName(nodeId, variableName, helperIndex);
        StringBuilder helper = new StringBuilder();
        helper.append("    private ").append(normalizedType).append(" ").append(helperName).append("() {\n");
        List<String> bodyLines = sanitizeScriptBody(trimmed, imports);
        if (bodyLines.isEmpty()) {
            helper.append("        return ").append(defaultValue(normalizedType)).append(";\n");
        } else {
            boolean hasReturn = false;
            for (String line : bodyLines) {
                helper.append("        ").append(line).append("\n");
                if (line.trim().startsWith("return ")) {
                    hasReturn = true;
                }
            }
            if (!hasReturn) {
                helper.append("        return ").append(defaultValue(normalizedType)).append(";\n");
            }
        }
        helper.append("    }\n");

        return new RenderedInput(
                indent + normalizedType + " " + variableName + " = " + helperName + "();\n",
                helper.toString());
    }

    private List<String> sanitizeScriptBody(String script, TreeSet<String> imports) {
        List<String> lines = new ArrayList<>();
        for (String rawLine : script.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("import ")) {
                String normalizedImport = line.endsWith(";") ? line : line + ";";
                imports.add(normalizedImport.substring("import ".length(), normalizedImport.length() - 1));
                continue;
            }
            if (!line.endsWith(";") && !line.endsWith("{") && !line.endsWith("}")) {
                line = line + ";";
            }
            lines.add(line);
        }
        return lines;
    }

    private boolean isInlineExpression(String script) {
        return !script.contains("\n")
                && !script.contains("\r")
                && !script.startsWith("import ")
                && !script.startsWith("return ");
    }

    private boolean isSafeJavaInlineExpression(String script) {
        return !script.contains("{")
                && !script.contains("}")
                && !script.contains(":");
    }

    private String buildHelperName(String nodeId, String variableName, int helperIndex) {
        String sanitizedNodeId = nodeId.replaceAll("[^a-zA-Z0-9]", "_");
        return "resolve_" + sanitizedNodeId + "_" + variableName + "_" + helperIndex;
    }

    private String normalizeTypeName(String typeName) {
        return typeName == null || typeName.isBlank() ? "Object" : typeName;
    }

    @SuppressWarnings("unchecked")
    private String resolveOutputContextKey(Map<String, Object> config) {
        Object outputObj = config.get("output");
        if (!(outputObj instanceof Map<?, ?> rawOutput)) {
            return null;
        }
        Object contextKey = ((Map<String, Object>) rawOutput).get("contextKey");
        return asString(contextKey);
    }

    private String defaultValue(String typeName) {
        return switch (typeName) {
            case "boolean" -> "false";
            case "byte", "short", "int", "long", "float", "double" -> "0";
            case "char" -> "'\\0'";
            default -> "null";
        };
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String escapeJava(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record RenderedInput(String statement, String helperMethod) {
    }
}

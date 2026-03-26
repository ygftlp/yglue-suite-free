package org.yglue.flow.orch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerArgMeta;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerSuggestRequest;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerSuggestResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParamAssemblerServiceSuggestTest {

    private ParamAssemblerService service;

    @BeforeEach
    void setUp() {
        ProjectService projectService = mock(ProjectService.class);
        Project project = new Project();
        project.setKey("demo");
        when(projectService.requireProject("demo")).thenReturn(project);
        service = new ParamAssemblerService(projectService, null, null, null, null, null);
    }

    @Test
    void suggestEmptyOnlyFillsBlankFieldsAndPreservesExistingMappings() {
        ParamAssemblerSuggestRequest request = new ParamAssemblerSuggestRequest();
        request.setMode("emptyOnly");
        request.setArgs(List.of(objectArgMeta("order", orderSchema())));
        request.setSourcePaths(List.of("request.body.order.userId", "context.tenantId"));

        Map<String, Object> userIdField = fieldNode("userId", sourceNode("request", "request.body.overrideUserId"));
        Map<String, Object> tenantField = fieldNode("tenantId", sourceNode("request", ""));
        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("name", "order");
        arg.put("javaType", "com.demo.OrderRequest");
        arg.put("required", true);
        arg.put("value", Map.of(
                "kind", "object",
                "fields", List.of(userIdField, tenantField)
        ));

        request.setAst(Map.of(
                "version", "param-ast/v1",
                "temps", List.of(),
                "args", List.of(arg)
        ));

        ParamAssemblerSuggestResponse response = service.suggest("demo", request);
        List<Map<String, Object>> fields = fieldsOf(response.getAst(), 0);

        assertEquals("request.body.overrideUserId", sourcePathOfField(fields.get(0)));
        assertEquals("context.tenantId", sourcePathOfField(fields.get(1)));
        assertEquals(1, response.getUpdatedCount());
        assertEquals(List.of("order"), response.getTouchedArgs());
        assertTrue(response.getSummary().contains("共更新 1 处配置"));
    }

    @Test
    void suggestEmptyOnlyBackfillsListItemFieldsWithItemPaths() {
        ParamAssemblerSuggestRequest request = new ParamAssemblerSuggestRequest();
        request.setMode("emptyOnly");
        request.setArgs(List.of(listArgMeta("skuList", skuListSchema())));
        request.setSourcePaths(List.of("request.body.skuList"));

        Map<String, Object> itemField = fieldNode("skuId", sourceNode("item", ""));
        Map<String, Object> listValue = new LinkedHashMap<>();
        listValue.put("kind", "list");
        listValue.put("source", sourceNode("request", ""));
        listValue.put("item", Map.of(
                "kind", "object",
                "fields", List.of(itemField)
        ));
        listValue.put("ops", List.of());

        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("name", "skuList");
        arg.put("javaType", "java.util.List<com.demo.SkuDto>");
        arg.put("required", true);
        arg.put("value", listValue);
        request.setAst(Map.of(
                "version", "param-ast/v1",
                "temps", List.of(),
                "args", List.of(arg)
        ));

        ParamAssemblerSuggestResponse response = service.suggest("demo", request);
        Map<String, Object> suggestedArg = argOf(response.getAst(), 0);
        Map<String, Object> suggestedValue = castMap(suggestedArg.get("value"));
        List<Map<String, Object>> itemFields = asMapList(castMap(suggestedValue.get("item")).get("fields"));

        assertEquals("request.body.skuList", sourcePathOfSourceNode(castMap(suggestedValue.get("source"))));
        assertEquals("item.skuId", sourcePathOfField(itemFields.get(0)));
        assertEquals(2, response.getUpdatedCount());
    }

    private static ParamAssemblerArgMeta objectArgMeta(String name, Map<String, Object> schema) {
        ParamAssemblerArgMeta meta = new ParamAssemblerArgMeta();
        meta.setName(name);
        meta.setJavaType("com.demo." + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        meta.setRequired(true);
        meta.setSchema(schema);
        return meta;
    }

    private static ParamAssemblerArgMeta listArgMeta(String name, Map<String, Object> schema) {
        ParamAssemblerArgMeta meta = new ParamAssemblerArgMeta();
        meta.setName(name);
        meta.setJavaType("java.util.List<com.demo.SkuDto>");
        meta.setRequired(true);
        meta.setSchema(schema);
        return meta;
    }

    private static Map<String, Object> orderSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of("type", "string"),
                        "tenantId", Map.of("type", "string")
                )
        );
    }

    private static Map<String, Object> skuListSchema() {
        return Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "skuId", Map.of("type", "string")
                        )
                )
        );
    }

    private static Map<String, Object> fieldNode(String path, Map<String, Object> source) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("path", path);
        field.put("value", source);
        return field;
    }

    private static Map<String, Object> sourceNode(String sourceType, String path) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "source");
        source.put("sourceType", sourceType);
        source.put("path", path);
        return source;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldsOf(Map<String, Object> ast, int argIndex) {
        return asMapList(castMap(argOf(ast, argIndex).get("value")).get("fields"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> argOf(Map<String, Object> ast, int index) {
        return asMapList(ast.get("args")).get(index);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object raw) {
        return (List<Map<String, Object>>) raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object raw) {
        return (Map<String, Object>) raw;
    }

    private static String sourcePathOfField(Map<String, Object> field) {
        return sourcePathOfSourceNode(castMap(field.get("value")));
    }

    private static String sourcePathOfSourceNode(Map<String, Object> source) {
        return String.valueOf(source.get("path"));
    }
}

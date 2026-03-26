package org.yglue.flow.orch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerArgMeta;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerValidateRequest;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerValidateResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParamAssemblerServiceListValidationTest {

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
    void validateAcceptsServiceCallAsListSource() {
        ParamAssemblerValidateRequest request = new ParamAssemblerValidateRequest();
        request.setArgs(List.of(listArgMeta("skuCodes", "java.util.List<java.lang.String>")));
        Map<String, Object> listValue = new LinkedHashMap<>();
        listValue.put("kind", "list");
        listValue.put("source", serviceCallNode(
                "skuFacade",
                "querySkuList",
                List.of(Map.of(
                        "name", "tenantId",
                        "value", sourceNode("context", "tenant.id")))));
        listValue.put("item", sourceNode("item", "item.code"));
        listValue.put("ops", List.of(Map.of(
                "id", "filter_1",
                "op", "filter",
                "expression", "item.enabled == true")));

        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("name", "skuCodes");
        arg.put("javaType", "java.util.List<java.lang.String>");
        arg.put("required", true);
        arg.put("value", listValue);

        Map<String, Object> ast = new LinkedHashMap<>();
        ast.put("version", "param-ast/v1");
        ast.put("temps", List.of());
        ast.put("args", List.of(arg));
        request.setAst(ast);

        ParamAssemblerValidateResponse response = service.validate("demo", request);

        assertTrue(response.isValid());
        assertTrue(response.getIssues().stream().noneMatch(item -> "error".equalsIgnoreCase(item.getSeverity())));
    }

    private static ParamAssemblerArgMeta listArgMeta(String name, String javaType) {
        ParamAssemblerArgMeta meta = new ParamAssemblerArgMeta();
        meta.setName(name);
        meta.setJavaType(javaType);
        meta.setRequired(true);
        return meta;
    }

    private static Map<String, Object> sourceNode(String sourceType, String path) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "source");
        source.put("sourceType", sourceType);
        source.put("path", path);
        return source;
    }

    private static Map<String, Object> serviceCallNode(String serviceBean,
                                                       String methodName,
                                                       List<Map<String, Object>> args) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("kind", "call");
        call.put("callType", "service");
        call.put("fn", serviceBean + "." + methodName);
        call.put("ref", Map.of(
                "serviceBean", serviceBean,
                "methodName", methodName,
                "returnType", "java.util.List<com.demo.SkuDto>"
        ));
        call.put("args", args);
        return call;
    }
}

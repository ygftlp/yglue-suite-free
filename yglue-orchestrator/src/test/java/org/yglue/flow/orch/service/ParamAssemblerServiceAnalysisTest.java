package org.yglue.flow.orch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.ProjectEndpoint;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerArgMeta;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerValidateRequest;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerAnalysisResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParamAssemblerServiceAnalysisTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ParamAssemblerService service;
    private ProjectEndpointService projectEndpointService;

    @BeforeEach
    void setUp() {
        ProjectService projectService = mock(ProjectService.class);
        projectEndpointService = mock(ProjectEndpointService.class);
        Project project = new Project();
        project.setKey("demo");
        when(projectService.requireProject("demo")).thenReturn(project);
        service = new ParamAssemblerService(projectService, projectEndpointService, null, null, null, null);
    }

    @Test
    void analyzeBuildsTempDependencyAndComplexitySummary() throws Exception {
        when(projectEndpointService.list("demo")).thenReturn(List.of(serviceEndpoint(
                "profileFacade",
                List.of(operation(
                        "queryProfile",
                        "queryProfile(java.lang.String)",
                        "sig-profile",
                        "com.demo.ProfileDto",
                        List.of(param("userId", "java.lang.String")))))));

        ParamAssemblerAnalysisResponse response = service.analyze("demo", request());

        assertEquals(1, response.getSummary().getArgCount());
        assertEquals(1, response.getSummary().getTempCount());
        assertEquals(1, response.getSummary().getServiceCallCount());
        assertEquals(1, response.getSummary().getTempReferenceCount());
        assertEquals("medium", response.getSummary().getComplexityLevel());

        assertEquals(1, response.getTempItems().size());
        assertEquals("profile", response.getTempItems().get(0).getKey());
        assertTrue(response.getTempItems().get(0).getSourceRefs().contains("request.body.userId"));
        assertTrue(response.getTempItems().get(0).getUsedBy().contains("arg:order"));

        assertEquals(1, response.getArgItems().size());
        assertEquals("order", response.getArgItems().get(0).getName());
        assertTrue(response.getArgItems().get(0).getTempRefs().contains("temp.profile"));
        assertTrue(response.getIssues().isEmpty());
        assertTrue(response.getRiskItems().isEmpty());
    }

    private static ParamAssemblerValidateRequest request() {
        ParamAssemblerValidateRequest request = new ParamAssemblerValidateRequest();
        request.setArgs(List.of(argMeta("order", "com.demo.OrderRequest")));

        Map<String, Object> temp = new LinkedHashMap<>();
        temp.put("key", "profile");
        temp.put("javaType", "com.demo.ProfileDto");
        temp.put("value", serviceCallNode(
                "profileFacade",
                "queryProfile",
                "queryProfile(java.lang.String)",
                "sig-profile",
                "com.demo.ProfileDto",
                List.of(callArg("userId", sourceNode("request", "request.body.userId")))));

        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("name", "order");
        arg.put("javaType", "com.demo.OrderRequest");
        arg.put("required", true);
        arg.put("value", Map.of(
                "kind", "object",
                "fields", List.of(
                        fieldNode("userId", sourceNode("request", "request.body.userId")),
                        fieldNode("profile", sourceNode("temp", "temp.profile"))
                )
        ));

        Map<String, Object> ast = new LinkedHashMap<>();
        ast.put("version", "param-ast/v1");
        ast.put("temps", List.of(temp));
        ast.put("args", List.of(arg));
        request.setAst(ast);
        return request;
    }

    private static ParamAssemblerArgMeta argMeta(String name, String javaType) {
        ParamAssemblerArgMeta meta = new ParamAssemblerArgMeta();
        meta.setName(name);
        meta.setJavaType(javaType);
        meta.setRequired(true);
        return meta;
    }

    private static Map<String, Object> serviceCallNode(String serviceBean,
                                                       String methodName,
                                                       String methodSignature,
                                                       String methodSignatureHash,
                                                       String returnType,
                                                       List<Map<String, Object>> args) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("kind", "call");
        call.put("callType", "service");
        call.put("fn", serviceBean + "." + methodName);
        call.put("ref", Map.of(
                "serviceBean", serviceBean,
                "methodName", methodName,
                "methodSignature", methodSignature,
                "methodSignatureHash", methodSignatureHash,
                "returnType", returnType
        ));
        call.put("args", args);
        return call;
    }

    private static Map<String, Object> callArg(String name, Map<String, Object> value) {
        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("name", name);
        arg.put("value", value);
        return arg;
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

    private static Map<String, Object> operation(String methodName,
                                                 String methodSignature,
                                                 String methodSignatureHash,
                                                 String returnType,
                                                 List<Map<String, Object>> params) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("method", methodName);
        op.put("methodSignature", methodSignature);
        op.put("methodSignatureHash", methodSignatureHash);
        op.put("returnType", returnType);
        op.put("params", params);
        return op;
    }

    private static Map<String, Object> param(String name, String type) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", name);
        param.put("type", type);
        return param;
    }

    private static ProjectEndpoint serviceEndpoint(String bean, List<Map<String, Object>> operations) throws JsonProcessingException {
        ProjectEndpoint endpoint = new ProjectEndpoint();
        endpoint.setEndpointType("SERVICE");
        endpoint.setConfigJson(OBJECT_MAPPER.writeValueAsString(Map.of(
                "bean", bean,
                "operations", operations
        )));
        return endpoint;
    }
}

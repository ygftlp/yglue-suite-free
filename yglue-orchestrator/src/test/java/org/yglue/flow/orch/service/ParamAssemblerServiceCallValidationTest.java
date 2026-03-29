package org.yglue.flow.orch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.ProjectEndpoint;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerArgMeta;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerValidateRequest;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerValidateResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParamAssemblerServiceCallValidationTest {

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
    void validateRejectsStaleServiceSignatureHash() throws Exception {
        when(projectEndpointService.list("demo")).thenReturn(List.of(serviceEndpoint(
                "profileFacade",
                List.of(operation(
                        "queryProfile",
                        "queryProfile(java.lang.String)",
                        "sig-current",
                        "com.demo.ProfileDto",
                        List.of(param("customerId", "java.lang.String")))))));

        ParamAssemblerValidateResponse response = service.validate("demo", requestWithCall(serviceCallNode(
                "profileFacade",
                "queryProfile",
                "queryProfile(java.lang.String)",
                "sig-stale",
                "com.demo.ProfileDto",
                List.of(callArg("customerId")))));

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(item -> "call.service.signature.mismatch".equals(item.getCode())));
    }

    @Test
    void validateRejectsUnknownAndMissingCallArgsAgainstMetadata() throws Exception {
        when(projectEndpointService.list("demo")).thenReturn(List.of(serviceEndpoint(
                "profileFacade",
                List.of(operation(
                        "queryProfile",
                        "queryProfile(java.lang.String,java.lang.String)",
                        "sig-profile",
                        "com.demo.ProfileDto",
                        List.of(
                                param("tenantId", "java.lang.String"),
                                param("customerId", "java.lang.String")))))));

        ParamAssemblerValidateResponse response = service.validate("demo", requestWithCall(serviceCallNode(
                "profileFacade",
                "queryProfile",
                "queryProfile(java.lang.String,java.lang.String)",
                "sig-profile",
                "com.demo.ProfileDto",
                List.of(
                        callArg("customerId"),
                        callArg("tenant")))));

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(item -> "call.arg.name.unknown".equals(item.getCode())));
        assertTrue(response.getIssues().stream().anyMatch(item -> "call.arg.missing".equals(item.getCode())));
    }

    @Test
    void validateAcceptsMatchingServiceMetadata() throws Exception {
        when(projectEndpointService.list("demo")).thenReturn(List.of(serviceEndpoint(
                "profileFacade",
                List.of(operation(
                        "queryProfile",
                        "queryProfile(java.lang.String,java.lang.String)",
                        "sig-profile",
                        "com.demo.ProfileDto",
                        List.of(
                                param("tenantId", "java.lang.String"),
                                param("customerId", "java.lang.String")))))));

        ParamAssemblerValidateResponse response = service.validate("demo", requestWithCall(serviceCallNode(
                "profileFacade",
                "queryProfile",
                "queryProfile(java.lang.String,java.lang.String)",
                "sig-profile",
                "com.demo.ProfileDto",
                List.of(
                        callArg("tenantId"),
                        callArg("customerId")))));

        assertTrue(response.isValid());
        assertTrue(response.getIssues().stream().noneMatch(item -> "error".equalsIgnoreCase(item.getSeverity())));
    }

    private static ParamAssemblerValidateRequest requestWithCall(Map<String, Object> callNode) {
        ParamAssemblerValidateRequest request = new ParamAssemblerValidateRequest();
        request.setArgs(List.of(argMeta("profile")));

        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("name", "profile");
        arg.put("javaType", "com.demo.ProfileDto");
        arg.put("required", true);
        arg.put("value", callNode);

        Map<String, Object> ast = new LinkedHashMap<>();
        ast.put("version", "param-ast/v1");
        ast.put("temps", List.of());
        ast.put("args", List.of(arg));
        request.setAst(ast);
        return request;
    }

    private static ParamAssemblerArgMeta argMeta(String name) {
        ParamAssemblerArgMeta meta = new ParamAssemblerArgMeta();
        meta.setName(name);
        meta.setJavaType("com.demo.ProfileDto");
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

    private static Map<String, Object> callArg(String name) {
        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("name", name);
        arg.put("value", Map.of(
                "kind", "source",
                "sourceType", "const",
                "constValue", name + "-value"
        ));
        return arg;
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

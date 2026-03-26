package org.yglue.flow.orch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.yglue.flow.orch.domain.FlowModel;
import org.yglue.flow.orch.domain.FlowResolver;
import org.yglue.flow.orch.domain.Project;
import org.yglue.flow.orch.domain.ProjectEndpoint;
import org.yglue.flow.orch.domain.code.ProjectClassAggregate;
import org.yglue.flow.orch.domain.code.ProjectJarDependency;
import org.yglue.flow.orch.domain.jar.JarClassAggregate;
import org.yglue.flow.orch.persistence.mapper.JarLibraryMapper;
import org.yglue.flow.orch.persistence.mapper.ProjectCodeMapper;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectComponentGroupResponse;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectComponentItemResponse;
import org.yglue.flow.orch.web.dto.endpoint.response.ProjectEndpointResponse;
import org.yglue.flow.orch.web.dto.flow.response.FlowModelResponse;
import org.yglue.flow.orch.web.dto.flow.response.FlowResolverResponse;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerArgMeta;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerDraftRequest;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerSuggestRequest;
import org.yglue.flow.orch.web.dto.paramassembler.request.ParamAssemblerValidateRequest;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerContextResponse;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerDraftResponse;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerIssueResponse;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerSuggestResponse;
import org.yglue.flow.orch.web.dto.paramassembler.response.ParamAssemblerValidateResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ParamAssemblerService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String AST_VERSION = "param-ast/v1";
    private static final int DEFAULT_HELPER_LIMIT = 800;
    private static final int MAX_HELPER_LIMIT = 2000;
    private static final Set<String> SOURCE_TYPES = Set.of("request", "context", "temp", "nodeOutput", "const", "item");
    private static final Set<String> LIST_OPS = Set.of("filter", "map", "compose");
    private static final Set<String> BRANCH_TEMP_KINDS = Set.of("ctx", "tempVar", "const", "expression", "serviceCall");
    private static final Set<String> BRANCH_CONDITION_GROUP_OPS = Set.of("and", "or");
    private static final Set<String> BRANCH_CONDITION_RULE_OPS = Set.of("eq", "ne", "gt", "ge", "lt", "le", "contains", "in");
    private static final Set<String> BRANCH_CONDITION_SOURCE_KINDS = Set.of("ctx", "const", "tempVar", "serviceCall");
    private static final Map<String, String> COMPONENT_DISPLAY_NAMES = Map.of(
            "BUSINESS", "Business Components",
            "SYSTEM", "System Components",
            "VALIDATOR", "Validator Components"
    );
    private static final Map<String, Integer> COMPONENT_ORDER = Map.of(
            "BUSINESS", 0,
            "SYSTEM", 1,
            "VALIDATOR", 2
    );

    private final ProjectService projectService;
    private final ProjectEndpointService projectEndpointService;
    private final FlowModelService flowModelService;
    private final FlowResolverService flowResolverService;
    private final ProjectCodeMapper projectCodeMapper;
    private final JarLibraryMapper jarLibraryMapper;

    public ParamAssemblerService(ProjectService projectService,
                                 ProjectEndpointService projectEndpointService,
                                 FlowModelService flowModelService,
                                 FlowResolverService flowResolverService,
                                 ProjectCodeMapper projectCodeMapper,
                                 JarLibraryMapper jarLibraryMapper) {
        this.projectService = projectService;
        this.projectEndpointService = projectEndpointService;
        this.flowModelService = flowModelService;
        this.flowResolverService = flowResolverService;
        this.projectCodeMapper = projectCodeMapper;
        this.jarLibraryMapper = jarLibraryMapper;
    }

    public ParamAssemblerContextResponse buildContext(String projectKey, Long endpointId) {
        Project project = projectService.requireProject(projectKey);
        ParamAssemblerContextResponse response = new ParamAssemblerContextResponse();

        if (endpointId != null) {
            ProjectEndpoint endpoint = projectEndpointService.get(projectKey, endpointId);
            ProjectEndpointResponse entrypoint = toEndpointResponse(endpoint);
            response.setEntrypoint(entrypoint);
            response.setSourcePaths(buildSourcePaths(entrypoint.getRequestSchemaJson()));
        }

        response.setComponentGroups(buildComponentGroups(projectKey));
        response.setModels(flowModelService.listActive(project.getId()).stream()
                .map(FlowModelResponse::from)
                .collect(Collectors.toList()));
        response.setResolvers(flowResolverService.listActive(project.getId()).stream()
                .map(FlowResolverResponse::from)
                .collect(Collectors.toList()));
        fillHelperContext(project.getId(), response, DEFAULT_HELPER_LIMIT);
        return response;
    }

    public ParamAssemblerDraftResponse buildDraft(String projectKey, ParamAssemblerDraftRequest request) {
        projectService.requireProject(projectKey);
        ParamAssemblerDraftResponse response = new ParamAssemblerDraftResponse();
        Map<String, Object> ast = new LinkedHashMap<>();
        ast.put("version", AST_VERSION);
        ast.put("temps", new ArrayList<>());

        List<Map<String, Object>> args = new ArrayList<>();
        List<String> sourcePaths = normalizeSourcePaths(request.getSourcePaths());
        for (ParamAssemblerArgMeta arg : safeArgs(request.getArgs())) {
            Map<String, Object> argNode = new LinkedHashMap<>();
            argNode.put("name", text(arg.getName()));
            argNode.put("javaType", text(arg.getJavaType()));
            argNode.put("required", Boolean.TRUE.equals(arg.getRequired()));
            argNode.put("value", buildDraftValue(arg, sourcePaths));
            args.add(argNode);
        }
        ast.put("args", args);

        response.setAst(ast);
        response.setIssues(validateAst(ast, safeArgs(request.getArgs())));
        return response;
    }

    public ParamAssemblerValidateResponse validate(String projectKey, ParamAssemblerValidateRequest request) {
        projectService.requireProject(projectKey);
        ParamAssemblerValidateResponse response = new ParamAssemblerValidateResponse();
        List<ParamAssemblerIssueResponse> issues = validateAst(request.getAst(), safeArgs(request.getArgs()));
        response.setIssues(issues);
        response.setValid(issues.stream().noneMatch(item -> "error".equalsIgnoreCase(item.getSeverity())));
        return response;
    }

    public ParamAssemblerSuggestResponse suggest(String projectKey, ParamAssemblerSuggestRequest request) {
        projectService.requireProject(projectKey);
        List<ParamAssemblerArgMeta> args = safeArgs(request.getArgs());
        List<String> baseSourcePaths = normalizeSourcePaths(request.getSourcePaths());
        Map<String, Object> ast = prepareSuggestAst(request.getAst(), args, baseSourcePaths);
        SuggestStats stats = applyAstSuggestions(ast, args, baseSourcePaths, isSuggestAllMode(request.getMode()));

        ParamAssemblerSuggestResponse response = new ParamAssemblerSuggestResponse();
        response.setAst(ast);
        response.setUpdatedCount(stats.updatedCount());
        response.setTouchedArgs(stats.touchedArgs());
        response.setSummary(buildSuggestSummary(stats, request.getMode()));
        response.setIssues(validateAst(ast, args));
        return response;
    }

    public String prepareFlowContentForSave(String projectKey, String contentJson) {
        projectService.requireProject(projectKey);
        return prepareFlowContent(contentJson, false);
    }

    public String prepareFlowContentForPublish(String projectKey, String contentJson) {
        projectService.requireProject(projectKey);
        return prepareFlowContent(contentJson, true);
    }

    public Map<String, Object> compileAstToParamPlans(Map<String, Object> ast,
                                                      List<ParamAssemblerArgMeta> argsMeta) {
        Map<String, Object> compiled = new LinkedHashMap<>();
        compiled.put("tempPlans", compileTempPlans(ast));
        compiled.put("argPlans", compileArgPlans(ast, argsMeta));
        return compiled;
    }

    private String prepareFlowContent(String contentJson, boolean strict) {
        if (!StringUtils.hasText(contentJson)) {
            return contentJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(contentJson);
            if (!(root instanceof ObjectNode objectRoot)) {
                if (strict) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Flow content must be a JSON object.");
                }
                return contentJson;
            }

            JsonNode nodesNode = objectRoot.get("nodes");
            if (!(nodesNode instanceof ArrayNode nodes)) {
                return contentJson;
            }

            List<String> errors = new ArrayList<>();
            boolean changed = false;
            for (JsonNode node : nodes) {
                if (node instanceof ObjectNode nodeObject) {
                    changed = prepareNodeContent(nodeObject, strict, errors) || changed;
                }
            }
            if (strict) {
                validateBranchAuthoring(objectRoot, nodes, errors);
            }

            if (strict && !errors.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ; ", errors));
            }
            return changed ? OBJECT_MAPPER.writeValueAsString(objectRoot) : contentJson;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            if (strict) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Flow content JSON is invalid for param-ast compilation: " + ex.getMessage());
            }
            log.warn("Skip param-ast compilation on save because flow JSON is invalid: {}", ex.getMessage());
            return contentJson;
        }
    }

    private boolean prepareNodeContent(ObjectNode node, boolean strict, List<String> errors) {
        ObjectNode data = objectNode(node.get("data"));
        if (data == null) {
            return false;
        }

        List<ParamAssemblerArgMeta> argsMeta = extractArgMetas(data.get("inputs"));
        String nodeRef = buildNodeRef(node, data);
        boolean changed = false;

        JsonNode astNode = findParamAstNode(data);
        if (astNode != null && !astNode.isNull()) {
            Map<String, Object> ast = castMap(OBJECT_MAPPER.convertValue(astNode, new TypeReference<Map<String, Object>>() {}));
            List<ParamAssemblerIssueResponse> astIssues = validateAst(ast, argsMeta);
            boolean hasErrors = astIssues.stream().anyMatch(item -> "error".equalsIgnoreCase(item.getSeverity()));
            if (strict && hasErrors) {
                astIssues.stream()
                        .filter(item -> "error".equalsIgnoreCase(item.getSeverity()))
                        .map(item -> nodeRef + " -> " + item.getPath() + " : " + item.getMessage())
                        .forEach(errors::add);
            }

            if (!strict || !hasErrors) {
                try {
                    Map<String, Object> compiled = compileAstToParamPlans(ast, argsMeta);
                    data.set("paramPlans", OBJECT_MAPPER.valueToTree(compiled));
                    changed = true;
                    if (strict) {
                        validateLegacyParamPlans(compiled, argsMeta).stream()
                                .map(message -> nodeRef + " -> " + message)
                                .forEach(errors::add);
                    }
                } catch (IllegalArgumentException ex) {
                    if (strict) {
                        errors.add(nodeRef + " -> " + ex.getMessage());
                    } else if (data.has("paramPlans")) {
                        data.remove("paramPlans");
                        changed = true;
                    }
                }
            } else if (!strict && data.has("paramPlans")) {
                data.remove("paramPlans");
                changed = true;
            }
        }

        JsonNode legacyPlansNode = data.get("paramPlans");
        if (strict && legacyPlansNode != null && legacyPlansNode.isObject()) {
            Map<String, Object> paramPlans = castMap(OBJECT_MAPPER.convertValue(legacyPlansNode, new TypeReference<Map<String, Object>>() {}));
            validateLegacyParamPlans(paramPlans, argsMeta).stream()
                    .map(message -> nodeRef + " -> " + message)
                    .forEach(errors::add);
        }

        return changed;
    }

    private void validateBranchAuthoring(ObjectNode root, ArrayNode nodes, List<String> errors) {
        Map<String, BranchValidationContext> branchContexts = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            if (!(node instanceof ObjectNode nodeObject)) {
                continue;
            }
            ObjectNode data = objectNode(nodeObject.get("data"));
            if (!isBranchNode(nodeObject, data)) {
                continue;
            }
            BranchValidationContext context = buildBranchValidationContext(nodeObject, data, errors);
            if (context != null && StringUtils.hasText(context.nodeId())) {
                branchContexts.put(context.nodeId(), context);
            }
        }

        JsonNode edgesNode = root.get("edges");
        if (!(edgesNode instanceof ArrayNode edges) || branchContexts.isEmpty()) {
            return;
        }
        for (JsonNode edge : edges) {
            if (edge instanceof ObjectNode edgeObject) {
                validateBranchEdge(edgeObject, branchContexts, errors);
            }
        }
    }

    private BranchValidationContext buildBranchValidationContext(ObjectNode node,
                                                                ObjectNode data,
                                                                List<String> errors) {
        String nodeId = text(node.path("id").asText(null));
        String nodeRef = buildNodeRef(node, data);
        List<Map<String, Object>> tempVars = jsonMapList(data.get("tempVars"));
        Map<String, Integer> tempOrderByKey = new LinkedHashMap<>();

        for (int index = 0; index < tempVars.size(); index += 1) {
            Map<String, Object> plan = tempVars.get(index);
            String key = normalizeBranchTempKey(text(plan.get("key")));
            if (!StringUtils.hasText(key)) {
                errors.add(nodeRef + " -> tempVars[" + index + "].key : Branch tempVar key is required.");
                continue;
            }
            Integer previousIndex = tempOrderByKey.putIfAbsent(key, index);
            if (previousIndex != null) {
                errors.add(nodeRef + " -> tempVars[" + index + "].key : Duplicated branch tempVar key: " + key);
            }
        }

        for (int index = 0; index < tempVars.size(); index += 1) {
            validateBranchTempVar(tempVars.get(index), index, nodeRef, tempOrderByKey, errors);
        }
        return new BranchValidationContext(nodeId, nodeRef, tempOrderByKey);
    }

    private void validateBranchTempVar(Map<String, Object> plan,
                                       int index,
                                       String nodeRef,
                                       Map<String, Integer> tempOrderByKey,
                                       List<String> errors) {
        String location = "tempVars[" + index + "]";
        String kind = text(plan.get("kind"));
        if (!StringUtils.hasText(kind)) {
            kind = "ctx";
        }
        if (!BRANCH_TEMP_KINDS.contains(kind)) {
            errors.add(nodeRef + " -> " + location + ".kind : Unsupported branch tempVar kind: " + kind);
            return;
        }

        String key = normalizeBranchTempKey(text(plan.get("key")));
        switch (kind) {
            case "ctx" -> {
                if (!StringUtils.hasText(text(plan.get("path")))) {
                    errors.add(nodeRef + " -> " + location + ".path : Branch ctx tempVar requires path.");
                }
            }
            case "tempVar" -> validateBranchTempReference(
                    text(plan.get("tempKey")),
                    key,
                    tempOrderByKey,
                    index,
                    nodeRef,
                    location + ".tempKey",
                    errors);
            case "expression" -> {
                String expression = firstNonBlank(text(plan.get("expression")), text(plan.get("constValue")));
                if (!StringUtils.hasText(expression)) {
                    errors.add(nodeRef + " -> " + location + ".expression : Branch expression tempVar requires expression.");
                }
            }
            case "serviceCall" -> {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("kind", "serviceCall");
                source.put("serviceCall", castMap(plan.get("serviceCall")));
                source.put("serviceResultPath", firstNonBlank(text(plan.get("serviceResultPath")), text(plan.get("resultPath"))));
                validateLegacyCallSource(source, location + ".serviceCall", errors);
                validateBranchLegacySourceTempRefs(
                        source,
                        tempOrderByKey,
                        index,
                        key,
                        nodeRef,
                        location + ".serviceCall",
                        errors);
            }
            default -> {
                // const allows blank string and does not need additional validation.
            }
        }
    }

    private void validateBranchEdge(ObjectNode edge,
                                    Map<String, BranchValidationContext> branchContexts,
                                    List<String> errors) {
        String sourceId = text(edge.path("source").asText(null));
        BranchValidationContext context = branchContexts.get(sourceId);
        if (context == null) {
            return;
        }
        ObjectNode data = objectNode(edge.get("data"));
        if (data == null) {
            return;
        }

        Map<String, Object> condition = jsonMap(data.get("conditionV2"));
        if (condition.isEmpty()) {
            condition = jsonMap(data.get("condition"));
        }
        if (condition.isEmpty()) {
            return;
        }

        String edgeRef = buildEdgeRef(edge, data, context.nodeRef());
        validateBranchConditionTree(condition, edgeRef, context, errors);
    }

    private void validateBranchConditionTree(Map<String, Object> tree,
                                             String edgeRef,
                                             BranchValidationContext context,
                                             List<String> errors) {
        String groupOp = text(tree.get("op"));
        if (StringUtils.hasText(groupOp) && !BRANCH_CONDITION_GROUP_OPS.contains(groupOp)) {
            errors.add(edgeRef + " -> condition.op : Unsupported branch condition group op: " + groupOp);
        }

        List<Map<String, Object>> rules = asMapList(tree.get("rules"));
        for (int index = 0; index < rules.size(); index += 1) {
            Map<String, Object> rule = rules.get(index);
            String rulePath = "condition.rules[" + index + "]";
            String op = text(rule.get("op"));
            if (StringUtils.hasText(op) && !BRANCH_CONDITION_RULE_OPS.contains(op)) {
                errors.add(edgeRef + " -> " + rulePath + ".op : Unsupported branch rule op: " + op);
            }
            validateBranchConditionSource(castMap(rule.get("left")), edgeRef, context, rulePath + ".left", errors);
            validateBranchConditionSource(castMap(rule.get("right")), edgeRef, context, rulePath + ".right", errors);
        }
    }

    private void validateBranchConditionSource(Map<String, Object> source,
                                               String edgeRef,
                                               BranchValidationContext context,
                                               String location,
                                               List<String> errors) {
        if (source.isEmpty()) {
            errors.add(edgeRef + " -> " + location + " : Branch condition source is required.");
            return;
        }

        String kind = text(source.get("kind"));
        if (!StringUtils.hasText(kind)) {
            kind = source.containsKey("constValue") ? "const" : "";
        }
        if (!BRANCH_CONDITION_SOURCE_KINDS.contains(kind)) {
            errors.add(edgeRef + " -> " + location + ".kind : Unsupported branch condition source kind: " + kind);
            return;
        }

        switch (kind) {
            case "ctx" -> {
                if (!StringUtils.hasText(text(source.get("path")))) {
                    errors.add(edgeRef + " -> " + location + ".path : Branch condition ctx source requires path.");
                }
            }
            case "tempVar" -> validateBranchTempReference(
                    text(source.get("tempKey")),
                    null,
                    context.tempOrderByKey(),
                    null,
                    edgeRef,
                    location + ".tempKey",
                    errors);
            case "serviceCall" -> {
                validateLegacyCallSource(source, location, errors);
                validateBranchLegacySourceTempRefs(
                        source,
                        context.tempOrderByKey(),
                        null,
                        null,
                        edgeRef,
                        location,
                        errors);
            }
            default -> {
                // const is always allowed, including empty string.
            }
        }
    }

    private void validateBranchLegacySourceTempRefs(Map<String, Object> source,
                                                    Map<String, Integer> tempOrderByKey,
                                                    Integer currentIndex,
                                                    String currentKey,
                                                    String ownerRef,
                                                    String location,
                                                    List<String> errors) {
        List<TempReference> refs = new ArrayList<>();
        collectLegacyTempReferences(source, location, refs);
        for (TempReference ref : refs) {
            validateBranchTempReference(
                    ref.targetKey(),
                    currentKey,
                    tempOrderByKey,
                    currentIndex,
                    ownerRef,
                    ref.path(),
                    errors);
        }
    }

    private void collectLegacyTempReferences(Map<String, Object> source,
                                             String location,
                                             Collection<TempReference> refs) {
        if (source == null || source.isEmpty()) {
            return;
        }

        String kind = text(source.get("kind"));
        String mode = text(source.get("mode"));
        if ("objectBuilder".equalsIgnoreCase(mode)) {
            List<Map<String, Object>> objectFields = asMapList(source.get("objectFields"));
            for (int index = 0; index < objectFields.size(); index += 1) {
                collectLegacyTempReferences(
                        castMap(objectFields.get(index).get("source")),
                        location + ".objectFields[" + index + "].source",
                        refs);
            }
            return;
        }

        if ("tempVar".equals(kind)) {
            String tempKey = normalizeBranchTempReference(text(source.get("tempKey")));
            if (StringUtils.hasText(tempKey)) {
                refs.add(new TempReference(firstSegment(tempKey), location));
            }
            return;
        }

        if ("serviceCall".equals(kind)) {
            Map<String, Object> serviceCall = castMap(source.get("serviceCall"));
            List<Map<String, Object>> argBindings = asMapList(serviceCall.get("argBindings"));
            for (int index = 0; index < argBindings.size(); index += 1) {
                collectLegacyTempReferences(
                        castMap(argBindings.get(index).get("source")),
                        location + ".serviceCall.argBindings[" + index + "].source",
                        refs);
            }
        }
    }

    private void validateBranchTempReference(String rawRef,
                                             String currentKey,
                                             Map<String, Integer> tempOrderByKey,
                                             Integer currentIndex,
                                             String ownerRef,
                                             String location,
                                             List<String> errors) {
        String normalized = normalizeBranchTempReference(rawRef);
        String rootKey = firstSegment(normalized);
        if (!StringUtils.hasText(rootKey)) {
            errors.add(ownerRef + " -> " + location + " : Branch tempVar reference is required.");
            return;
        }
        if (StringUtils.hasText(currentKey) && rootKey.equals(currentKey)) {
            errors.add(ownerRef + " -> " + location + " : Branch tempVar cannot reference itself: " + rootKey);
            return;
        }
        Integer targetIndex = tempOrderByKey.get(rootKey);
        if (targetIndex == null) {
            errors.add(ownerRef + " -> " + location + " : Referenced branch tempVar does not exist: " + rootKey);
            return;
        }
        if (currentIndex != null && targetIndex >= currentIndex) {
            errors.add(ownerRef + " -> " + location + " : Branch tempVar can only reference an earlier tempVar: " + rootKey);
        }
    }

    private boolean isBranchNode(ObjectNode node, ObjectNode data) {
        if (node == null) {
            return false;
        }
        String type = text(node.path("type").asText(null));
        if ("branch".equals(type)) {
            return true;
        }
        return data != null && Boolean.TRUE.equals(data.get("branch") == null ? null : data.get("branch").asBoolean(false));
    }

    private String buildEdgeRef(ObjectNode edge, ObjectNode data, String branchRef) {
        String source = text(edge.path("source").asText(null));
        String target = text(edge.path("target").asText(null));
        String label = text(data.path("label").asText(null));
        String priority = text(data.path("priority").asText(null));
        if (StringUtils.hasText(label)) {
            return branchRef + " edge(" + label + ")";
        }
        if (StringUtils.hasText(priority)) {
            return branchRef + " edge(priority=" + priority + ", target=" + firstNonBlank(target, "unknown") + ")";
        }
        return branchRef + " edge(" + firstNonBlank(source, "unknown") + " -> " + firstNonBlank(target, "unknown") + ")";
    }

    private String normalizeBranchTempKey(String value) {
        return normalizeKey(value);
    }

    private String normalizeBranchTempReference(String value) {
        String normalized = normalizeKey(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.startsWith("tempVar(") && normalized.endsWith(")")) {
            normalized = normalized.substring("tempVar(".length(), normalized.length() - 1).trim();
        }
        if (normalized.startsWith("temp.")) {
            normalized = normalized.substring("temp.".length()).trim();
        }
        return normalized;
    }

    private String normalizeKey(String value) {
        return text(value);
    }

    private String firstSegment(String value) {
        String normalized = text(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        int dotIndex = normalized.indexOf('.');
        if (dotIndex < 0) {
            return normalized;
        }
        return normalized.substring(0, dotIndex);
    }

    private JsonNode findParamAstNode(ObjectNode data) {
        for (String key : List.of("paramAst", "paramAssemblerAst", "paramAstV1")) {
            JsonNode node = data.get(key);
            if (node != null && !node.isNull()) {
                return node;
            }
        }
        ObjectNode comp = objectNode(data.get("comp"));
        if (comp == null) {
            return null;
        }
        String configJson = text(comp.path("configJson").asText(null));
        if (!StringUtils.hasText(configJson)) {
            return null;
        }
        try {
            JsonNode configNode = OBJECT_MAPPER.readTree(configJson);
            if (configNode instanceof ObjectNode configObject) {
                for (String key : List.of("paramAst", "paramAssemblerAst", "paramAstV1")) {
                    JsonNode node = configObject.get(key);
                    if (node != null && !node.isNull()) {
                        return node;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse service component configJson for param-ast lookup: {}", ex.getMessage());
        }
        return null;
    }

    private List<ParamAssemblerArgMeta> extractArgMetas(JsonNode inputsNode) {
        if (!(inputsNode instanceof ArrayNode inputs)) {
            return List.of();
        }
        List<ParamAssemblerArgMeta> output = new ArrayList<>();
        for (JsonNode item : inputs) {
            if (!(item instanceof ObjectNode input)) {
                continue;
            }
            String name = text(input.path("name").asText(null));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            ParamAssemblerArgMeta meta = new ParamAssemblerArgMeta();
            meta.setName(name);
            meta.setRequired(input.path("required").asBoolean(false));
            meta.setJavaType(resolveInputJavaType(input));
            meta.setSchema(castMap(OBJECT_MAPPER.convertValue(input.get("schema"), new TypeReference<Map<String, Object>>() {})));
            output.add(meta);
        }
        return output;
    }

    private String resolveInputJavaType(ObjectNode input) {
        String typeName = text(input.path("typeName").asText(null));
        if (StringUtils.hasText(typeName)) {
            return typeName;
        }
        JsonNode schemaNode = input.get("schema");
        if (schemaNode instanceof ObjectNode schema) {
            String javaType = text(schema.path("x-javaType").asText(null));
            if (StringUtils.hasText(javaType)) {
                return javaType;
            }
        }
        String valueType = text(input.path("valueType").asText(null)).toUpperCase(Locale.ROOT);
        return switch (valueType) {
            case "STRING" -> "java.lang.String";
            case "BOOLEAN" -> "java.lang.Boolean";
            case "NUMBER" -> "java.lang.Number";
            case "ARRAY" -> "java.util.List<java.lang.Object>";
            case "OBJECT" -> "java.lang.Object";
            default -> "";
        };
    }

    private String buildNodeRef(ObjectNode node, ObjectNode data) {
        String id = text(node.path("id").asText(null));
        String label = text(data.path("label").asText(null));
        if (StringUtils.hasText(label) && StringUtils.hasText(id)) {
            return label + " (" + id + ")";
        }
        return StringUtils.hasText(label) ? label : firstNonBlank(id, "service-node");
    }

    private List<Map<String, Object>> compileTempPlans(Map<String, Object> ast) {
        List<Map<String, Object>> output = new ArrayList<>();
        List<Map<String, Object>> temps = asMapList(ast.get("temps"));
        for (int index = 0; index < temps.size(); index += 1) {
            Map<String, Object> temp = temps.get(index);
            String key = text(temp.get("key"));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("key", key);
            plan.put("typeHint", text(temp.get("javaType")));
            compileValueIntoPlan(castMap(temp.get("value")), plan, "temps[" + index + "]");
            output.add(plan);
        }
        return output;
    }

    private List<Map<String, Object>> compileArgPlans(Map<String, Object> ast,
                                                      List<ParamAssemblerArgMeta> argsMeta) {
        Map<String, ParamAssemblerArgMeta> metaByName = new HashMap<>();
        for (ParamAssemblerArgMeta meta : safeArgs(argsMeta)) {
            if (StringUtils.hasText(meta.getName())) {
                metaByName.put(meta.getName(), meta);
            }
        }

        List<Map<String, Object>> output = new ArrayList<>();
        List<Map<String, Object>> args = asMapList(ast.get("args"));
        for (int index = 0; index < args.size(); index += 1) {
            Map<String, Object> arg = args.get(index);
            String name = text(arg.get("name"));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("target", name);
            ParamAssemblerArgMeta meta = metaByName.get(name);
            plan.put("typeHint", meta != null ? text(meta.getJavaType()) : text(arg.get("javaType")));
            compileValueIntoPlan(castMap(arg.get("value")), plan, "args[" + index + "]");
            output.add(plan);
        }
        return output;
    }

    private void compileValueIntoPlan(Map<String, Object> value, Map<String, Object> plan, String location) {
        String kind = text(value.get("kind")).toLowerCase(Locale.ROOT);
        switch (kind) {
            case "source" -> plan.put("source", compileSourceNode(value, false, location));
            case "call" -> plan.put("source", compileCallNode(value, location, false));
            case "expr", "expression" -> plan.put("source", compileExpressionNode(value));
            case "object" -> plan.put("source", compileObjectNode(value, location, false));
            case "list" -> compileListNode(value, plan, location);
            case "coalesce" -> throw unsupported(location, "coalesce is not supported by the phase-1 compiler");
            default -> throw unsupported(location, "unsupported param-ast kind: " + kind);
        }
    }

    private Map<String, Object> compileSourceNode(Map<String, Object> value,
                                                  boolean listItemContext,
                                                  String location) {
        String sourceType = text(value.get("sourceType"));
        String path = text(value.get("path"));
        if ("const".equals(sourceType)) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("kind", "const");
            source.put("constValue", value.containsKey("value") ? value.get("value") : value.get("constValue"));
            return source;
        }
        if ("temp".equals(sourceType)) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("kind", "tempVar");
            source.put("tempKey", stripPrefix(path, "temp."));
            return source;
        }
        if ("item".equals(sourceType)) {
            if (!listItemContext) {
                throw unsupported(location, "item source can only be used inside list item mapping");
            }
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("kind", "ctx");
            String itemPath = stripPrefix(path, "item.");
            source.put("path", StringUtils.hasText(itemPath) ? "$." + itemPath : "$");
            return source;
        }

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "ctx");
        source.put("path", path);
        return source;
    }

    private Map<String, Object> compileExpressionNode(Map<String, Object> value) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "expr");
        source.put("expression", firstNonBlank(text(value.get("expression")), text(value.get("value"))));
        return source;
    }

    private Map<String, Object> compileCallNode(Map<String, Object> value,
                                                String location,
                                                boolean listItemContext) {
        String callType = text(value.get("callType")).toLowerCase(Locale.ROOT);
        if ("service".equals(callType)) {
            Map<String, Object> serviceCall = new LinkedHashMap<>();
            serviceCall.put("fn", text(value.get("fn")));
            serviceCall.put("serviceRef", compileCallRef(castMap(value.get("ref"))));
            serviceCall.put("argBindings", compileCallArgs(asMapList(value.get("args")), location, listItemContext));

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("kind", "serviceCall");
            source.put("serviceCall", serviceCall);
            String resultPath = text(value.get("resultPath"));
            if (StringUtils.hasText(resultPath)) {
                source.put("serviceResultPath", resultPath);
            }
            return source;
        }
        if ("http".equals(callType)) {
            if (listItemContext) {
                throw unsupported(location, "http call is not supported inside list item mapping");
            }
            Map<String, Object> ref = castMap(value.get("ref"));
            Map<String, Object> httpCall = new LinkedHashMap<>();
            httpCall.put("method", firstNonBlank(text(value.get("method")), text(ref.get("method")), "GET"));
            httpCall.put("url", firstNonBlank(text(value.get("url")), text(ref.get("url"))));
            copyIfPresent(value, httpCall, "headers");
            copyIfPresent(value, httpCall, "query");
            copyIfPresent(value, httpCall, "bodyJson");
            copyIfPresent(value, httpCall, "timeoutSeconds");
            copyIfPresent(value, httpCall, "retryCount");
            copyIfPresent(value, httpCall, "retryBackoffMs");
            copyIfPresent(value, httpCall, "retryOnStatuses");
            copyIfPresent(value, httpCall, "resultPath");

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("kind", "httpCall");
            source.put("httpCall", httpCall);
            return source;
        }
        throw unsupported(location, "unsupported callType: " + callType);
    }

    private Map<String, Object> compileCallRef(Map<String, Object> ref) {
        Map<String, Object> output = new LinkedHashMap<>();
        copyIfPresent(ref, output, "serviceBean");
        copyIfPresent(ref, output, "serviceName");
        copyIfPresent(ref, output, "serviceClass");
        copyIfPresent(ref, output, "methodName");
        copyIfPresent(ref, output, "methodSignature");
        copyIfPresent(ref, output, "methodSignatureHash");
        copyIfPresent(ref, output, "returnType");
        return output;
    }

    private List<Map<String, Object>> compileCallArgs(List<Map<String, Object>> args,
                                                      String location,
                                                      boolean listItemContext) {
        List<Map<String, Object>> output = new ArrayList<>();
        for (int index = 0; index < args.size(); index += 1) {
            Map<String, Object> arg = args.get(index);
            Map<String, Object> binding = new LinkedHashMap<>();
            binding.put("name", text(arg.get("name")));
            binding.put("source", compileValueAsBindingSource(castMap(arg.get("value")),
                    location + ".args[" + index + "]",
                    listItemContext));
            output.add(binding);
        }
        return output;
    }

    private Map<String, Object> compileObjectNode(Map<String, Object> value,
                                                  String location,
                                                  boolean listItemContext) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "ctx");
        source.put("mode", "objectBuilder");

        List<Map<String, Object>> objectFields = new ArrayList<>();
        collectObjectFields(value, "", objectFields, location, listItemContext);
        source.put("objectFields", objectFields);
        return source;
    }

    private void collectObjectFields(Map<String, Object> objectNode,
                                     String prefix,
                                     List<Map<String, Object>> output,
                                     String location,
                                     boolean listItemContext) {
        List<Map<String, Object>> fields = asMapList(objectNode.get("fields"));
        for (int index = 0; index < fields.size(); index += 1) {
            Map<String, Object> field = fields.get(index);
            String rawPath = text(field.get("path"));
            if (!StringUtils.hasText(rawPath)) {
                continue;
            }
            String mergedPath = StringUtils.hasText(prefix) ? prefix + "." + rawPath : rawPath;
            ensureSupportedFieldPath(mergedPath, location + ".fields[" + index + "]");

            Map<String, Object> fieldValue = castMap(field.get("value"));
            if ("object".equalsIgnoreCase(text(fieldValue.get("kind")))) {
                collectObjectFields(fieldValue, mergedPath, output, location + ".fields[" + index + "]", listItemContext);
                continue;
            }
            if ("list".equalsIgnoreCase(text(fieldValue.get("kind")))) {
                throw unsupported(location + ".fields[" + index + "]", "nested array or collection fields are not supported");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fieldPath", mergedPath);
            item.put("source", compileValueAsBindingSource(fieldValue, location + ".fields[" + index + "]", listItemContext));
            output.add(item);
        }
    }

    private Map<String, Object> compileValueAsBindingSource(Map<String, Object> value,
                                                            String location,
                                                            boolean listItemContext) {
        String kind = text(value.get("kind")).toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "source" -> compileSourceNode(value, listItemContext, location);
            case "call" -> compileCallNode(value, location, listItemContext);
            case "expr", "expression" -> compileExpressionNode(value);
            case "object" -> compileObjectNode(value, location, listItemContext);
            case "coalesce" -> throw unsupported(location, "coalesce is not supported inside legacy binding source");
            case "list" -> throw unsupported(location, "list source is not supported inside nested binding");
            default -> throw unsupported(location, "unsupported nested value kind: " + kind);
        };
    }

    private void compileListNode(Map<String, Object> value, Map<String, Object> plan, String location) {
        Map<String, Object> planSource = new LinkedHashMap<>();
        planSource.put("kind", "listPipeline");
        plan.put("source", planSource);
        plan.put("listInput", compileListInput(castMap(value.get("source")), location + ".source"));

        List<Map<String, Object>> listSteps = new ArrayList<>();
        Map<String, Object> listCompose = null;

        Map<String, Object> item = castMap(value.get("item"));
        if (!item.isEmpty()) {
            CompiledListItem compiledItem = compileListItem(item, location + ".item");
            listSteps.addAll(compiledItem.listSteps());
            listCompose = compiledItem.listCompose();
        }

        List<Map<String, Object>> ops = asMapList(value.get("ops"));
        for (int index = 0; index < ops.size(); index += 1) {
            Map<String, Object> op = ops.get(index);
            String opName = text(op.get("op"));
            if ("filter".equals(opName)) {
                listSteps.add(Map.of(
                        "op", "filter",
                        "exprText", requireExpression(op, location + ".ops[" + index + "]")));
                continue;
            }
            if ("map".equals(opName)) {
                Map<String, Object> opValue = castMap(op.get("value"));
                if (!opValue.isEmpty()) {
                    CompiledListItem mappedItem = compileListItem(opValue, location + ".ops[" + index + "]");
                    if (mappedItem.listCompose() != null) {
                        if (listCompose != null) {
                            throw unsupported(location + ".ops[" + index + "]", "multiple list compose definitions are not supported");
                        }
                        listCompose = mappedItem.listCompose();
                    }
                    listSteps.addAll(mappedItem.listSteps());
                } else {
                    listSteps.add(Map.of(
                            "op", "map",
                            "exprText", requireExpression(op, location + ".ops[" + index + "]")));
                }
                continue;
            }
            if ("compose".equals(opName)) {
                if (listCompose != null) {
                    throw unsupported(location + ".ops[" + index + "]", "multiple list compose definitions are not supported");
                }
                listCompose = compileComposeNode(op, location + ".ops[" + index + "]");
                continue;
            }
            throw unsupported(location + ".ops[" + index + "]", "unsupported list op: " + opName);
        }

        if (!listSteps.isEmpty()) {
            plan.put("listSteps", listSteps);
        }
        if (listCompose != null) {
            plan.put("listCompose", listCompose);
        }
    }

    private Map<String, Object> compileListInput(Map<String, Object> value, String location) {
        String kind = text(value.get("kind")).toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "source" -> {
                if ("item".equals(text(value.get("sourceType")))) {
                    throw unsupported(location, "list input cannot come from item context");
                }
                yield compileSourceNode(value, false, location);
            }
            case "call" -> {
                if (!"service".equals(text(value.get("callType")).toLowerCase(Locale.ROOT))) {
                    throw unsupported(location, "list input only supports service call in phase 1");
                }
                yield compileCallNode(value, location, false);
            }
            default -> throw unsupported(location, "list input only supports source or service call");
        };
    }

    private CompiledListItem compileListItem(Map<String, Object> value, String location) {
        String kind = text(value.get("kind")).toLowerCase(Locale.ROOT);
        if ("object".equals(kind)) {
            return new CompiledListItem(List.of(), compileObjectToListCompose(value, location));
        }
        if ("source".equals(kind)) {
            String sourceType = text(value.get("sourceType"));
            String path = text(value.get("path"));
            if (!"item".equals(sourceType)) {
                throw unsupported(location, "primitive list item mapping only supports item source");
            }
            String itemPath = stripPrefix(path, "item.");
            if (!StringUtils.hasText(itemPath)) {
                return new CompiledListItem(List.of(), null);
            }
            return new CompiledListItem(List.of(Map.of("op", "map", "exprText", itemPath)), null);
        }
        if ("expr".equals(kind) || "expression".equals(kind)) {
            return new CompiledListItem(List.of(Map.of(
                    "op", "map",
                    "exprText", firstNonBlank(text(value.get("expression")), text(value.get("value"))))), null);
        }
        throw unsupported(location, "list item only supports object, item source, or expression");
    }

    private Map<String, Object> compileComposeNode(Map<String, Object> value, String location) {
        List<Map<String, Object>> fields = asMapList(value.get("fields"));
        if (!fields.isEmpty()) {
            Map<String, Object> compose = new LinkedHashMap<>();
            compose.put("fields", compileComposeFields(fields, location));
            return compose;
        }
        return compileObjectToListCompose(value, location);
    }

    private Map<String, Object> compileObjectToListCompose(Map<String, Object> objectNode, String location) {
        Map<String, Object> compose = new LinkedHashMap<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        collectComposeFields(objectNode, "", fields, location);
        compose.put("fields", fields);
        return compose;
    }

    private List<Map<String, Object>> compileComposeFields(List<Map<String, Object>> fields, String location) {
        List<Map<String, Object>> output = new ArrayList<>();
        for (int index = 0; index < fields.size(); index += 1) {
            Map<String, Object> field = fields.get(index);
            String targetField = text(field.get("targetField"));
            ensureSupportedFieldPath(targetField, location + ".fields[" + index + "]");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetField", targetField);
            item.put("source", compileValueAsBindingSource(castMap(field.get("source")),
                    location + ".fields[" + index + "]",
                    true));
            output.add(item);
        }
        return output;
    }

    private void collectComposeFields(Map<String, Object> objectNode,
                                      String prefix,
                                      List<Map<String, Object>> output,
                                      String location) {
        List<Map<String, Object>> fields = asMapList(objectNode.get("fields"));
        for (int index = 0; index < fields.size(); index += 1) {
            Map<String, Object> field = fields.get(index);
            String rawPath = text(field.get("path"));
            if (!StringUtils.hasText(rawPath)) {
                continue;
            }
            String targetField = StringUtils.hasText(prefix) ? prefix + "." + rawPath : rawPath;
            ensureSupportedFieldPath(targetField, location + ".fields[" + index + "]");
            Map<String, Object> fieldValue = castMap(field.get("value"));
            if ("object".equalsIgnoreCase(text(fieldValue.get("kind")))) {
                collectComposeFields(fieldValue, targetField, output, location + ".fields[" + index + "]");
                continue;
            }
            if ("list".equalsIgnoreCase(text(fieldValue.get("kind")))) {
                throw unsupported(location + ".fields[" + index + "]", "nested array or collection fields are not supported");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetField", targetField);
            item.put("source", compileValueAsBindingSource(fieldValue, location + ".fields[" + index + "]", true));
            output.add(item);
        }
    }

    private String requireExpression(Map<String, Object> node, String location) {
        String expression = firstNonBlank(text(node.get("expr")), text(node.get("expression")), text(node.get("exprText")));
        if (!StringUtils.hasText(expression)) {
            throw unsupported(location, "expression is required");
        }
        return expression;
    }

    private void ensureSupportedFieldPath(String path, String location) {
        if (!StringUtils.hasText(path)) {
            throw unsupported(location, "field path is required");
        }
        if (path.contains("[") || path.contains("]")) {
            throw unsupported(location, "dynamic map key or nested array path is not supported");
        }
    }

    private String stripPrefix(String value, String prefix) {
        String text = text(value);
        if (text.startsWith(prefix)) {
            return text.substring(prefix.length());
        }
        return text;
    }

    private IllegalArgumentException unsupported(String location, String message) {
        return new IllegalArgumentException(location + " -> " + message);
    }

    private List<String> validateLegacyParamPlans(Map<String, Object> paramPlans,
                                                  List<ParamAssemblerArgMeta> argsMeta) {
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> argPlans = asMapList(paramPlans.get("argPlans"));
        Map<String, Map<String, Object>> argPlanByTarget = new LinkedHashMap<>();
        for (int index = 0; index < argPlans.size(); index += 1) {
            Map<String, Object> plan = argPlans.get(index);
            String target = text(plan.get("target"));
            if (!StringUtils.hasText(target)) {
                errors.add("argPlans[" + index + "] -> target is required");
                continue;
            }
            argPlanByTarget.putIfAbsent(target, plan);
            validateLegacyPlan(plan, "argPlans[" + index + "]", errors);
        }

        for (ParamAssemblerArgMeta meta : safeArgs(argsMeta)) {
            if (!Boolean.TRUE.equals(meta.getRequired())) {
                continue;
            }
            String name = text(meta.getName());
            Map<String, Object> plan = argPlanByTarget.get(name);
            if (plan == null) {
                errors.add("required argument is not mapped: " + name);
                continue;
            }
            Map<String, Object> source = castMap(plan.get("source"));
            if (!validateLegacySourceConfigured(source, plan)) {
                errors.add("required argument source is incomplete: " + name);
            }
        }

        List<Map<String, Object>> tempPlans = asMapList(paramPlans.get("tempPlans"));
        for (int index = 0; index < tempPlans.size(); index += 1) {
            Map<String, Object> plan = tempPlans.get(index);
            String key = text(plan.get("key"));
            if (!StringUtils.hasText(key)) {
                errors.add("tempPlans[" + index + "] -> key is required");
                continue;
            }
            validateLegacyPlan(plan, "tempPlans[" + index + "]", errors);
        }
        return errors;
    }

    private void validateLegacyPlan(Map<String, Object> plan, String location, List<String> errors) {
        Map<String, Object> source = castMap(plan.get("source"));
        if (source.isEmpty()) {
            errors.add(location + " -> source is required");
            return;
        }
        validateLegacySource(source, plan, location + ".source", errors);
    }

    private void validateLegacySource(Map<String, Object> source,
                                      Map<String, Object> ownerPlan,
                                      String location,
                                      List<String> errors) {
        String kind = text(source.get("kind"));
        switch (kind) {
            case "ctx" -> {
                if (!StringUtils.hasText(text(source.get("path")))) {
                    errors.add(location + " -> ctx source requires path");
                }
            }
            case "tempVar" -> {
                if (!StringUtils.hasText(text(source.get("tempKey")))) {
                    errors.add(location + " -> tempVar source requires tempKey");
                }
            }
            case "const" -> {
                if (!source.containsKey("constValue") && !source.containsKey("value")) {
                    errors.add(location + " -> const source requires constValue");
                }
            }
            case "expr", "expression" -> {
                if (!StringUtils.hasText(firstNonBlank(text(source.get("expression")), text(source.get("value"))))) {
                    errors.add(location + " -> expression source requires expression/value");
                }
            }
            case "serviceCall" -> validateLegacyCallSource(source, location, errors);
            case "httpCall" -> {
                Map<String, Object> httpCall = castMap(source.get("httpCall"));
                if (!StringUtils.hasText(text(httpCall.get("url")))) {
                    errors.add(location + " -> httpCall requires url");
                }
            }
            case "listPipeline" -> validateLegacyListPlan(ownerPlan, location, errors);
            default -> errors.add(location + " -> unsupported source kind: " + kind);
        }
    }

    private boolean validateLegacySourceConfigured(Map<String, Object> source, Map<String, Object> plan) {
        String kind = text(source.get("kind"));
        return switch (kind) {
            case "ctx" -> StringUtils.hasText(text(source.get("path")));
            case "tempVar" -> StringUtils.hasText(text(source.get("tempKey")));
            case "const" -> source.containsKey("constValue") || source.containsKey("value");
            case "expr", "expression" -> StringUtils.hasText(firstNonBlank(text(source.get("expression")), text(source.get("value"))));
            case "serviceCall" -> true;
            case "httpCall" -> true;
            case "listPipeline" -> {
                Map<String, Object> listInput = castMap(plan.get("listInput"));
                yield !listInput.isEmpty();
            }
            default -> false;
        };
    }

    private void validateLegacyCallSource(Map<String, Object> source, String location, List<String> errors) {
        Map<String, Object> serviceCall = castMap(source.get("serviceCall"));
        Map<String, Object> serviceRef = castMap(serviceCall.get("serviceRef"));
        String fn = text(serviceCall.get("fn"));
        if (!StringUtils.hasText(text(serviceRef.get("serviceBean")))
                || !StringUtils.hasText(text(serviceRef.get("methodName")))) {
            if (!StringUtils.hasText(fn) || !fn.contains(".")) {
                errors.add(location + " -> serviceCall requires serviceBean and methodName");
            }
        }

        List<Map<String, Object>> argBindings = asMapList(serviceCall.get("argBindings"));
        for (int index = 0; index < argBindings.size(); index += 1) {
            Map<String, Object> binding = argBindings.get(index);
            validateLegacyBindingSource(castMap(binding.get("source")), location + ".serviceCall.argBindings[" + index + "]", errors);
        }
    }

    private void validateLegacyBindingSource(Map<String, Object> source, String location, List<String> errors) {
        if (source.isEmpty()) {
            errors.add(location + " -> source is required");
            return;
        }
        String mode = text(source.get("mode"));
        if ("objectBuilder".equalsIgnoreCase(mode)) {
            List<Map<String, Object>> objectFields = asMapList(source.get("objectFields"));
            if (objectFields.isEmpty()) {
                errors.add(location + " -> objectBuilder requires objectFields");
                return;
            }
            for (int index = 0; index < objectFields.size(); index += 1) {
                Map<String, Object> field = objectFields.get(index);
                String fieldPath = text(field.get("fieldPath"));
                if (!StringUtils.hasText(fieldPath)) {
                    errors.add(location + ".objectFields[" + index + "] -> fieldPath is required");
                    continue;
                }
                if (fieldPath.contains("[") || fieldPath.contains("]")) {
                    errors.add(location + ".objectFields[" + index + "] -> dynamic map key or nested array path is not supported");
                }
                validateLegacySource(castMap(field.get("source")), Map.of(), location + ".objectFields[" + index + "].source", errors);
            }
            return;
        }
        validateLegacySource(source, Map.of(), location, errors);
    }

    private void validateLegacyListPlan(Map<String, Object> plan, String location, List<String> errors) {
        Map<String, Object> listInput = castMap(plan.get("listInput"));
        if (listInput.isEmpty()) {
            errors.add(location + " -> listPipeline requires listInput");
        } else {
            String inputKind = text(listInput.get("kind"));
            if ("httpCall".equals(inputKind)) {
                errors.add(location + " -> list input httpCall is not supported in phase 1");
            }
            validateLegacySource(listInput, Map.of(), location + ".listInput", errors);
        }

        List<Map<String, Object>> listSteps = asMapList(plan.get("listSteps"));
        for (int index = 0; index < listSteps.size(); index += 1) {
            Map<String, Object> step = listSteps.get(index);
            String op = text(step.get("op"));
            if (!LIST_OPS.contains(op)) {
                errors.add(location + ".listSteps[" + index + "] -> unsupported list op: " + op);
                continue;
            }
            if (("filter".equals(op) || "map".equals(op))
                    && !StringUtils.hasText(text(step.get("exprText")))) {
                errors.add(location + ".listSteps[" + index + "] -> exprText is required");
            }
        }

        Map<String, Object> listCompose = castMap(plan.get("listCompose"));
        List<Map<String, Object>> composeFields = asMapList(listCompose.get("fields"));
        for (int index = 0; index < composeFields.size(); index += 1) {
            Map<String, Object> field = composeFields.get(index);
            String targetField = text(field.get("targetField"));
            if (!StringUtils.hasText(targetField)) {
                errors.add(location + ".listCompose.fields[" + index + "] -> targetField is required");
                continue;
            }
            if (targetField.contains("[") || targetField.contains("]")) {
                errors.add(location + ".listCompose.fields[" + index + "] -> dynamic map key or nested array path is not supported");
            }
            validateLegacyBindingSource(castMap(field.get("source")), location + ".listCompose.fields[" + index + "].source", errors);
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private ObjectNode objectNode(JsonNode node) {
        return node instanceof ObjectNode objectNode ? objectNode : null;
    }

    private static final class CompiledListItem {
        private final List<Map<String, Object>> listSteps;
        private final Map<String, Object> listCompose;

        private CompiledListItem(List<Map<String, Object>> listSteps, Map<String, Object> listCompose) {
            this.listSteps = listSteps;
            this.listCompose = listCompose;
        }

        private List<Map<String, Object>> listSteps() {
            return listSteps;
        }

        private Map<String, Object> listCompose() {
            return listCompose;
        }
    }

    private static final class TempReference {
        private final String targetKey;
        private final String path;

        private TempReference(String targetKey, String path) {
            this.targetKey = targetKey;
            this.path = path;
        }

        private String targetKey() {
            return targetKey;
        }

        private String path() {
            return path;
        }
    }

    private static final class BranchValidationContext {
        private final String nodeId;
        private final String nodeRef;
        private final Map<String, Integer> tempOrderByKey;

        private BranchValidationContext(String nodeId,
                                        String nodeRef,
                                        Map<String, Integer> tempOrderByKey) {
            this.nodeId = nodeId;
            this.nodeRef = nodeRef;
            this.tempOrderByKey = tempOrderByKey;
        }

        private String nodeId() {
            return nodeId;
        }

        private String nodeRef() {
            return nodeRef;
        }

        private Map<String, Integer> tempOrderByKey() {
            return tempOrderByKey;
        }
    }

    private static final class ValidationContext {
        private final Map<String, Integer> tempOrderByKey;
        private final String currentTempKey;
        private final Integer currentTempIndex;

        private ValidationContext(Map<String, Integer> tempOrderByKey,
                                  String currentTempKey,
                                  Integer currentTempIndex) {
            this.tempOrderByKey = tempOrderByKey;
            this.currentTempKey = currentTempKey;
            this.currentTempIndex = currentTempIndex;
        }

        private static ValidationContext root(Map<String, Integer> tempOrderByKey) {
            return new ValidationContext(tempOrderByKey, null, null);
        }

        private static ValidationContext forTemp(Map<String, Integer> tempOrderByKey,
                                                 String currentTempKey,
                                                 Integer currentTempIndex) {
            return new ValidationContext(tempOrderByKey, currentTempKey, currentTempIndex);
        }

        private boolean hasTemp(String key) {
            return tempOrderByKey.containsKey(key);
        }

        private boolean isTempScope() {
            return StringUtils.hasText(currentTempKey) && currentTempIndex != null;
        }

        private boolean isForwardReference(String key) {
            Integer targetIndex = tempOrderByKey.get(key);
            return targetIndex != null && currentTempIndex != null && targetIndex > currentTempIndex;
        }

        private String currentTempKey() {
            return currentTempKey;
        }
    }

    private List<ProjectComponentGroupResponse> buildComponentGroups(String projectKey) {
        List<ProjectEndpointResponse> endpoints = projectEndpointService.list(projectKey).stream()
                .map(this::toEndpointResponse)
                .collect(Collectors.toList());
        if (endpoints.isEmpty()) {
            return List.of();
        }

        Map<String, List<ProjectEndpointResponse>> grouped = endpoints.stream()
                .collect(Collectors.groupingBy(item -> resolveComponentType(item.getComponentType(), item.getEndpointType())));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<ProjectEndpointResponse> sorted = new ArrayList<>(entry.getValue());
                    sorted.sort(Comparator.comparing(
                            item -> StringUtils.hasText(item.getName()) ? item.getName() : "",
                            String.CASE_INSENSITIVE_ORDER));

                    List<ProjectComponentItemResponse> items = sorted.stream()
                            .map(this::toComponentItem)
                            .collect(Collectors.toList());

                    return new ProjectComponentGroupResponse(
                            entry.getKey(),
                            COMPONENT_DISPLAY_NAMES.getOrDefault(entry.getKey(), entry.getKey()),
                            items
                    );
                })
                .sorted(Comparator.comparingInt(group -> COMPONENT_ORDER.getOrDefault(group.getType(), Integer.MAX_VALUE)))
                .collect(Collectors.toList());
    }

    private void fillHelperContext(Long projectId,
                                   ParamAssemblerContextResponse response,
                                   int limit) {
        int classLimit = Math.max(100, Math.min(MAX_HELPER_LIMIT, limit));
        List<ProjectJarDependency> deps = projectCodeMapper.listValidDependenciesByProject(projectId);
        List<ProjectJarDependency> selectedDeps = deps.stream()
                .filter(dep -> Boolean.TRUE.equals(dep.getSelected()))
                .collect(Collectors.toList());

        response.setSelectedJars(selectedDeps.stream()
                .map(this::toSelectedJarItem)
                .collect(Collectors.toList()));

        List<String> jarKeys = selectedDeps.stream()
                .map(ProjectJarDependency::getJarKey)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        List<ProjectClassAggregate> projectClasses =
                projectCodeMapper.listValidAggregatesByProjectLimited(projectId, classLimit);

        int remainingForJar = Math.max(0, classLimit - projectClasses.size());
        List<JarClassAggregate> jarClasses = (jarKeys.isEmpty() || remainingForJar == 0)
                ? List.of()
                : jarLibraryMapper.listJarAggregatesByJarKeysLimited(jarKeys, remainingForJar);

        Map<String, ParamAssemblerContextResponse.ClassRefItem> deduplicated = new LinkedHashMap<>();
        for (ProjectClassAggregate item : projectClasses) {
            addClassRef(deduplicated, item.getQualifiedName(), item.getSimpleName(), item.getPackageName(), item.getKind());
        }
        for (JarClassAggregate item : jarClasses) {
            addClassRef(deduplicated, item.getQualifiedName(), item.getSimpleName(), item.getPackageName(), item.getKind());
        }
        response.setHelperClasses(new ArrayList<>(deduplicated.values()));
    }

    private ProjectEndpointResponse toEndpointResponse(ProjectEndpoint endpoint) {
        ProjectEndpointResponse response = ProjectEndpointResponse.from(endpoint);
        response.setComponentType(resolveComponentType(response.getComponentType(), response.getEndpointType()));
        if (!StringUtils.hasText(endpoint.getConfigJson())) {
            return response;
        }
        try {
            Map<String, Object> config = OBJECT_MAPPER.readValue(endpoint.getConfigJson(), new TypeReference<Map<String, Object>>() {});
            Object requestSchemaJson = config.get("requestSchemaJson");
            if (requestSchemaJson != null) {
                response.setRequestSchemaJson(String.valueOf(requestSchemaJson));
            }
            Object responseSchema = config.get("responseSchema");
            if (responseSchema instanceof Map<?, ?> schemaMap) {
                response.setResponseSchema(castMap(schemaMap));
            }
        } catch (Exception ex) {
            log.warn("Failed to parse endpoint configJson. endpointId={}, error={}", endpoint.getId(), ex.getMessage());
        }
        return response;
    }

    private ProjectComponentItemResponse toComponentItem(ProjectEndpointResponse endpoint) {
        ProjectComponentItemResponse item = new ProjectComponentItemResponse();
        item.setId(endpoint.getId());
        item.setName(endpoint.getName());
        item.setDescription(endpoint.getDescription());
        item.setEndpointType(endpoint.getEndpointType());
        item.setComponentType(resolveComponentType(endpoint.getComponentType(), endpoint.getEndpointType()));
        item.setMethod(endpoint.getMethod());
        item.setPath(endpoint.getPath());
        item.setConfigJson(endpoint.getConfigJson());
        item.setCreateTime(endpoint.getCreateTime());
        item.setUpdateTime(endpoint.getUpdateTime());
        item.setEntrypointId(endpoint.getEntrypointId());
        item.setFlowCode(endpoint.getFlowCode());
        item.setEnabled(endpoint.getEnabled());
        return item;
    }

    private String resolveComponentType(String requested, String endpointType) {
        if (StringUtils.hasText(requested)) {
            return requested.trim().toUpperCase(Locale.ROOT);
        }
        if ("REST".equalsIgnoreCase(endpointType)) {
            return "SYSTEM";
        }
        if ("SERVICE".equalsIgnoreCase(endpointType)) {
            return "BUSINESS";
        }
        return "BUSINESS";
    }

    private ParamAssemblerContextResponse.SelectedJarItem toSelectedJarItem(ProjectJarDependency dep) {
        ParamAssemblerContextResponse.SelectedJarItem item = new ParamAssemblerContextResponse.SelectedJarItem();
        String coordinate = buildCoordinate(dep);
        item.setName(resolveJarName(dep, coordinate));
        item.setCoordinate(coordinate);
        item.setJarKey(dep.getJarKey());
        item.setGroupId(dep.getGroupId());
        item.setArtifactId(dep.getArtifactId());
        item.setVersion(dep.getVersion());
        return item;
    }

    private String resolveJarName(ProjectJarDependency dep, String coordinate) {
        if (StringUtils.hasText(dep.getArtifactId())) {
            return dep.getArtifactId();
        }
        if (StringUtils.hasText(dep.getJarKey())) {
            return dep.getJarKey();
        }
        return coordinate;
    }

    private String buildCoordinate(ProjectJarDependency dep) {
        if (StringUtils.hasText(dep.getJarKey())) {
            return dep.getJarKey();
        }
        List<String> parts = new ArrayList<>(3);
        if (StringUtils.hasText(dep.getGroupId())) {
            parts.add(dep.getGroupId());
        }
        if (StringUtils.hasText(dep.getArtifactId())) {
            parts.add(dep.getArtifactId());
        }
        if (StringUtils.hasText(dep.getVersion())) {
            parts.add(dep.getVersion());
        }
        return String.join(":", parts);
    }

    private void addClassRef(Map<String, ParamAssemblerContextResponse.ClassRefItem> sink,
                             String qualifiedName,
                             String simpleName,
                             String packageName,
                             String kind) {
        if (!StringUtils.hasText(qualifiedName) || sink.containsKey(qualifiedName)) {
            return;
        }
        ParamAssemblerContextResponse.ClassRefItem item = new ParamAssemblerContextResponse.ClassRefItem();
        item.setQualifiedName(qualifiedName);
        item.setSimpleName(simpleName);
        item.setPackageName(packageName);
        item.setKind(kind);
        sink.put(qualifiedName, item);
    }

    private List<String> buildSourcePaths(String requestSchemaJson) {
        if (!StringUtils.hasText(requestSchemaJson)) {
            return List.of();
        }
        try {
            Map<String, Object> schema = OBJECT_MAPPER.readValue(requestSchemaJson, new TypeReference<Map<String, Object>>() {});
            Object rawProperties = schema.get("properties");
            if (!(rawProperties instanceof Map<?, ?> properties)) {
                return List.of();
            }

            List<String> output = new ArrayList<>();
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String name = String.valueOf(entry.getKey());
                Map<String, Object> field = entry.getValue() instanceof Map<?, ?> rawField
                        ? castMap(rawField)
                        : Map.of();
                String source = text(field.get("x-source")).toLowerCase(Locale.ROOT);
                if ("path".equals(source)) {
                    output.add("request.path." + firstNonBlank(text(field.get("x-pathVariable")), name));
                    continue;
                }
                if ("query".equals(source)) {
                    output.add("request.query." + firstNonBlank(text(field.get("x-paramName")), name));
                    continue;
                }
                if ("header".equals(source)) {
                    output.add("request.headers." + firstNonBlank(text(field.get("x-headerName")), name));
                    continue;
                }
                if ("form".equals(source)) {
                    output.add("request.body." + firstNonBlank(text(field.get("x-formField")), name));
                    continue;
                }
                if ("body".equals(source)) {
                    appendSchemaPaths(field, "request.body", output);
                    continue;
                }
                output.add("request.body." + name);
            }
            return output.stream()
                    .filter(StringUtils::hasText)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.warn("Failed to parse request schema JSON for source paths: {}", ex.getMessage());
            return List.of();
        }
    }

    private void appendSchemaPaths(Map<String, Object> schema, String basePath, List<String> output) {
        String type = text(schema.get("type")).toLowerCase(Locale.ROOT);
        if ("array".equals(type)) {
            output.add(basePath + "[]");
            Object rawItems = schema.get("items");
            if (rawItems instanceof Map<?, ?> itemMap) {
                Map<String, Object> items = castMap(itemMap);
                if ("object".equals(text(items.get("type")).toLowerCase(Locale.ROOT)) && items.get("properties") instanceof Map<?, ?>) {
                    appendSchemaPaths(items, basePath + "[]", output);
                }
            }
            return;
        }

        Object rawProperties = schema.get("properties");
        if (!(rawProperties instanceof Map<?, ?> properties) || properties.isEmpty()) {
            if (StringUtils.hasText(basePath)) {
                output.add(basePath);
            }
            return;
        }

        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Map<String, Object> child = entry.getValue() instanceof Map<?, ?> rawChild
                    ? castMap(rawChild)
                    : Map.of();
            String childPath = StringUtils.hasText(basePath) ? basePath + "." + key : key;
            appendSchemaPaths(child, childPath, output);
        }
    }

    private Map<String, Object> buildDraftValue(ParamAssemblerArgMeta arg, List<String> sourcePaths) {
        Map<String, Object> schema = castMap(arg.getSchema());
        if (isListType(arg.getJavaType(), schema)) {
            return buildListDraft(arg, sourcePaths);
        }
        if (isObjectType(arg.getJavaType(), schema)) {
            return buildObjectDraft(text(arg.getName()), schema, sourcePaths);
        }
        String bestSourcePath = findBestSourcePath(text(arg.getName()), sourcePaths);
        return buildSourceDraft(bestSourcePath);
    }

    private Map<String, Object> buildObjectDraft(String targetName,
                                                 Map<String, Object> schema,
                                                 List<String> sourcePaths) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("kind", "object");

        Set<String> requiredLeafs = collectRequiredLeafPaths(schema);
        List<Map<String, Object>> fields = new ArrayList<>();
        for (String leafPath : flattenLeafPaths(schema)) {
            String bestSourcePath = findBestSourcePath(
                    firstNonBlank(targetName + "." + leafPath, leafPath),
                    sourcePaths);
            if (!StringUtils.hasText(bestSourcePath)) {
                bestSourcePath = findBestSourcePath(leafPath, sourcePaths);
            }
            if (!StringUtils.hasText(bestSourcePath) && !requiredLeafs.contains(normalizeLeaf(leafPath))) {
                continue;
            }

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("path", leafPath);
            field.put("value", buildSourceDraft(bestSourcePath));
            fields.add(field);
        }

        draft.put("fields", fields);
        if (StringUtils.hasText(targetName)) {
            draft.put("target", targetName);
        }
        return draft;
    }

    private Map<String, Object> buildListDraft(ParamAssemblerArgMeta arg, List<String> sourcePaths) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("kind", "list");

        String targetName = text(arg.getName());
        String bestSourcePath = findBestSourcePath(targetName + "[]", sourcePaths);
        if (!StringUtils.hasText(bestSourcePath)) {
            bestSourcePath = findBestSourcePath(targetName, sourcePaths);
        }
        draft.put("source", buildSourceDraft(bestSourcePath));
        draft.put("ops", new ArrayList<>());

        Map<String, Object> itemSchema = extractItemSchema(castMap(arg.getSchema()));
        if (isObjectType("", itemSchema)) {
            Set<String> requiredItemLeafs = collectRequiredLeafPaths(itemSchema);
            List<Map<String, Object>> fields = new ArrayList<>();
            for (String leafPath : flattenLeafPaths(itemSchema)) {
                String itemPath = "item." + leafPath;
                if (!requiredItemLeafs.contains(normalizeLeaf(leafPath)) && !StringUtils.hasText(itemPath)) {
                    continue;
                }
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("path", leafPath);
                field.put("value", buildSourceDraft(itemPath));
                fields.add(field);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", "object");
            item.put("fields", fields);
            draft.put("item", item);
        }
        return draft;
    }

    private Map<String, Object> buildSourceDraft(String sourcePath) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "source");
        source.put("sourceType", determineSourceType(sourcePath));
        source.put("path", text(sourcePath));
        return source;
    }

    private Map<String, Object> prepareSuggestAst(Map<String, Object> rawAst,
                                                  List<ParamAssemblerArgMeta> argsMeta,
                                                  List<String> sourcePaths) {
        Map<String, Object> ast = deepCopyMap(rawAst);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("version", AST_VERSION);
        normalized.put("temps", deepCopyList(asMapList(ast.get("temps"))));

        List<Map<String, Object>> rawArgs = asMapList(ast.get("args"));
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> rawArg : rawArgs) {
            String name = text(rawArg.get("name"));
            if (StringUtils.hasText(name)) {
                byName.put(name, rawArg);
            }
        }

        List<Map<String, Object>> normalizedArgs = new ArrayList<>();
        for (int index = 0; index < argsMeta.size(); index += 1) {
            ParamAssemblerArgMeta meta = argsMeta.get(index);
            Map<String, Object> rawArg = byName.get(text(meta.getName()));
            if (rawArg == null && index < rawArgs.size()) {
                rawArg = rawArgs.get(index);
            }
            Map<String, Object> argNode = new LinkedHashMap<>();
            argNode.put("name", text(meta.getName()));
            argNode.put("javaType", firstNonBlank(text(rawArg != null ? rawArg.get("javaType") : null), text(meta.getJavaType())));
            argNode.put("required", Boolean.TRUE.equals(meta.getRequired()));
            if (rawArg != null && rawArg.containsKey("value")) {
                argNode.put("value", deepCopyMapOrValue(rawArg.get("value")));
            } else {
                argNode.put("value", buildDraftValue(meta, sourcePaths));
            }
            normalizedArgs.add(argNode);
        }
        normalized.put("args", normalizedArgs);
        return normalized;
    }

    private SuggestStats applyAstSuggestions(Map<String, Object> ast,
                                             List<ParamAssemblerArgMeta> argsMeta,
                                             List<String> baseSourcePaths,
                                             boolean overrideAll) {
        List<String> sourcePaths = new ArrayList<>(baseSourcePaths);
        sourcePaths.addAll(buildTempSourcePaths(ast));
        sourcePaths = normalizeSourcePaths(sourcePaths);

        int updatedCount = 0;
        Set<String> touchedArgs = new LinkedHashSet<>();
        List<Map<String, Object>> args = asMapList(ast.get("args"));
        for (int index = 0; index < Math.min(args.size(), argsMeta.size()); index += 1) {
            Map<String, Object> arg = args.get(index);
            ParamAssemblerArgMeta meta = argsMeta.get(index);
            int changed = applyArgSuggestion(arg, meta, sourcePaths, !overrideAll);
            if (changed > 0) {
                updatedCount += changed;
                touchedArgs.add(text(meta.getName()));
            }
        }
        ast.put("args", args);
        return new SuggestStats(updatedCount, new ArrayList<>(touchedArgs));
    }

    private int applyArgSuggestion(Map<String, Object> arg,
                                   ParamAssemblerArgMeta meta,
                                   List<String> sourcePaths,
                                   boolean emptyOnly) {
        Map<String, Object> value = castMap(arg.get("value"));
        String kind = text(value.get("kind")).toLowerCase(Locale.ROOT);
        String argName = text(meta.getName());
        if ("source".equals(kind)) {
            return suggestSourceNode(value, "", argName, sourcePaths, emptyOnly);
        }
        if ("object".equals(kind)) {
            List<Map<String, Object>> fields = asMapList(value.get("fields"));
            int changed = suggestObjectFields(fields, argName, sourcePaths, emptyOnly, false);
            value.put("fields", fields);
            arg.put("value", value);
            return changed;
        }
        if (!"list".equals(kind)) {
            return 0;
        }

        int changed = 0;
        Map<String, Object> source = castMap(value.get("source"));
        if ("source".equalsIgnoreCase(text(source.get("kind")))) {
            String bestSourcePath = firstNonBlank(
                    findBestSourcePath(argName + "[]", sourcePaths),
                    findBestSourcePath(argName, sourcePaths));
            changed += suggestSourceNode(source, bestSourcePath, argName, sourcePaths, emptyOnly);
            value.put("source", source);
        }

        Map<String, Object> item = castMap(value.get("item"));
        String itemKind = text(item.get("kind")).toLowerCase(Locale.ROOT);
        if ("object".equals(itemKind)) {
            List<Map<String, Object>> itemFields = asMapList(item.get("fields"));
            changed += suggestObjectFields(itemFields, argName, sourcePaths, emptyOnly, true);
            item.put("fields", itemFields);
            value.put("item", item);
        }
        arg.put("value", value);
        return changed;
    }

    private int suggestObjectFields(List<Map<String, Object>> fields,
                                    String targetName,
                                    List<String> sourcePaths,
                                    boolean emptyOnly,
                                    boolean preferItemPath) {
        int changed = 0;
        for (Map<String, Object> field : fields) {
            String leafPath = text(field.get("path"));
            if (!StringUtils.hasText(leafPath)) {
                continue;
            }
            Map<String, Object> source = castMap(field.get("value"));
            String preferredPath = preferItemPath ? "item." + leafPath : "";
            String lookupTarget = firstNonBlank(targetName + "." + leafPath, leafPath);
            changed += suggestSourceNode(source, preferredPath, lookupTarget, sourcePaths, emptyOnly);
            field.put("value", source);
        }
        return changed;
    }

    private int suggestSourceNode(Map<String, Object> source,
                                  String preferredPath,
                                  String target,
                                  List<String> sourcePaths,
                                  boolean emptyOnly) {
        if (source.isEmpty() || !"source".equalsIgnoreCase(text(source.get("kind")))) {
            return 0;
        }
        if ("const".equalsIgnoreCase(text(source.get("sourceType")))) {
            return 0;
        }

        boolean blank = !StringUtils.hasText(text(source.get("path")));
        if (emptyOnly && !blank) {
            return 0;
        }

        String nextPath = firstNonBlank(preferredPath, findBestSourcePath(target, sourcePaths));
        if (!StringUtils.hasText(nextPath)) {
            return 0;
        }

        String nextSourceType = determineSourceType(nextPath);
        boolean changed = !nextPath.equals(text(source.get("path")))
                || !nextSourceType.equalsIgnoreCase(text(source.get("sourceType")))
                || StringUtils.hasText(text(source.get("constValue")));
        if (!changed) {
            return 0;
        }
        source.put("kind", "source");
        source.put("sourceType", nextSourceType);
        source.put("path", nextPath);
        if (source.containsKey("constValue")) {
            source.put("constValue", "");
        }
        return 1;
    }

    private String determineSourceType(String sourcePath) {
        String path = text(sourcePath);
        if (!StringUtils.hasText(path)) {
            return "request";
        }
        if (path.startsWith("request.")) {
            return "request";
        }
        if (path.startsWith("context.")) {
            return "context";
        }
        if (path.startsWith("temp.")) {
            return "temp";
        }
        if (path.startsWith("nodeOutput.")) {
            return "nodeOutput";
        }
        if (path.startsWith("item.")) {
            return "item";
        }
        return "request";
    }

    private List<ParamAssemblerIssueResponse> validateAst(Map<String, Object> ast, List<ParamAssemblerArgMeta> argsMeta) {
        List<ParamAssemblerIssueResponse> issues = new ArrayList<>();
        if (ast == null || ast.isEmpty()) {
            issues.add(error("ast.required", "ast", "Param assembler AST is required."));
            return issues;
        }

        String version = text(ast.get("version"));
        if (!AST_VERSION.equals(version)) {
            issues.add(error("ast.version.unsupported", "version", "Only param-ast/v1 is supported."));
        }

        List<Map<String, Object>> temps = asMapList(ast.get("temps"));
        Map<String, Integer> tempOrderByKey = buildTempOrderByKey(temps, issues);
        ValidationContext rootValidationContext = ValidationContext.root(tempOrderByKey);

        List<Map<String, Object>> argNodes = asMapList(ast.get("args"));
        Map<String, Map<String, Object>> argNodeByName = new LinkedHashMap<>();
        for (Map<String, Object> argNode : argNodes) {
            String name = text(argNode.get("name"));
            if (StringUtils.hasText(name) && !argNodeByName.containsKey(name)) {
                argNodeByName.put(name, argNode);
            }
        }

        for (int index = 0; index < argsMeta.size(); index += 1) {
            ParamAssemblerArgMeta meta = argsMeta.get(index);
            String argName = text(meta.getName());
            Map<String, Object> argNode = argNodeByName.get(argName);
            if (argNode == null && index < argNodes.size()) {
                argNode = argNodes.get(index);
            }
            if (argNode == null) {
                issues.add(error("arg.missing", "args[" + index + "]", "Missing argument node: " + argName));
                continue;
            }

            Map<String, Object> value = castMap(argNode.get("value"));
            if (value.isEmpty()) {
                issues.add(error("arg.value.missing", "args[" + index + "].value", "Argument value node is required."));
                continue;
            }
            issues.addAll(validateValue(value, "args[" + index + "].value", meta, rootValidationContext));
        }

        if (argNodes.size() > argsMeta.size()) {
            for (int index = argsMeta.size(); index < argNodes.size(); index += 1) {
                issues.add(warning("arg.extra", "args[" + index + "]", "Extra argument node is not present in the current method signature."));
            }
        }

        for (int index = 0; index < temps.size(); index += 1) {
            Map<String, Object> temp = temps.get(index);
            String key = text(temp.get("key"));
            if (!StringUtils.hasText(key)) {
                issues.add(error("temp.key.required", "temps[" + index + "].key", "Temp key is required."));
            }
            issues.addAll(validateValue(
                    castMap(temp.get("value")),
                    "temps[" + index + "].value",
                    null,
                    ValidationContext.forTemp(tempOrderByKey, key, index)));
        }
        issues.addAll(validateTempCycles(temps, tempOrderByKey));

        return issues;
    }

    private List<ParamAssemblerIssueResponse> validateValue(Map<String, Object> value,
                                                            String path,
                                                            ParamAssemblerArgMeta argMeta,
                                                            ValidationContext validationContext) {
        List<ParamAssemblerIssueResponse> issues = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            if (argMeta != null && Boolean.TRUE.equals(argMeta.getRequired())) {
                issues.add(error("value.required", path, "Required argument is not configured."));
            }
            return issues;
        }

        String kind = text(value.get("kind")).toLowerCase(Locale.ROOT);
        switch (kind) {
            case "source" -> issues.addAll(validateSource(value, path, validationContext));
            case "call" -> issues.addAll(validateCall(value, path, validationContext));
            case "object" -> issues.addAll(validateObject(value, path, argMeta, validationContext));
            case "list" -> issues.addAll(validateList(value, path, argMeta, validationContext));
            case "expr", "expression" -> {
                String expr = firstNonBlank(text(value.get("expression")), text(value.get("value")));
                if (!StringUtils.hasText(expr)) {
                    issues.add(error("expr.required", path, "Expression node requires expression or value."));
                }
            }
            case "coalesce" -> {
                List<Map<String, Object>> candidates = asMapList(value.get("candidates"));
                if (candidates.isEmpty()) {
                    issues.add(error("coalesce.candidates.required", path, "Coalesce node requires at least one candidate."));
                }
                for (int index = 0; index < candidates.size(); index += 1) {
                    issues.addAll(validateValue(candidates.get(index), path + ".candidates[" + index + "]", null, validationContext));
                }
            }
            default -> issues.add(error("value.kind.unsupported", path, "Unsupported value kind: " + kind));
        }
        return issues;
    }

    private List<ParamAssemblerIssueResponse> validateSource(Map<String, Object> source,
                                                             String path,
                                                             ValidationContext validationContext) {
        List<ParamAssemblerIssueResponse> issues = new ArrayList<>();
        String sourceType = text(source.get("sourceType"));
        if (!SOURCE_TYPES.contains(sourceType)) {
            issues.add(error("source.type.unsupported", path + ".sourceType", "Unsupported sourceType: " + sourceType));
        }

        if ("const".equals(sourceType)) {
            if (!source.containsKey("constValue")) {
                issues.add(error("source.const.required", path + ".constValue", "Const source requires constValue."));
            }
            return issues;
        }

        if (!StringUtils.hasText(text(source.get("path")))) {
            issues.add(error("source.path.required", path + ".path", "Source path is required."));
        } else if ("temp".equals(sourceType)) {
            String tempKey = extractTempKey(text(source.get("path")));
            if (!StringUtils.hasText(tempKey)) {
                issues.add(error("source.temp.key.required", path + ".path", "Temp source requires temp path."));
                return issues;
            }
            if (!validationContext.hasTemp(tempKey)) {
                issues.add(error("source.temp.missing", path + ".path", "Referenced temp does not exist: " + tempKey));
                return issues;
            }
            if (validationContext.isTempScope()) {
                if (tempKey.equals(validationContext.currentTempKey())) {
                    issues.add(error("source.temp.self", path + ".path", "Temp cannot reference itself: " + tempKey));
                } else if (validationContext.isForwardReference(tempKey)) {
                    issues.add(error("source.temp.forward", path + ".path", "Temp can only reference an earlier temp: " + tempKey));
                }
            }
        }
        return issues;
    }

    private List<ParamAssemblerIssueResponse> validateCall(Map<String, Object> call,
                                                           String path,
                                                           ValidationContext validationContext) {
        List<ParamAssemblerIssueResponse> issues = new ArrayList<>();
        String callType = text(call.get("callType")).toLowerCase(Locale.ROOT);
        if (!"service".equals(callType) && !"http".equals(callType)) {
            issues.add(error("call.type.unsupported", path + ".callType", "callType only supports service/http."));
            return issues;
        }

        Map<String, Object> ref = castMap(call.get("ref"));
        if ("service".equals(callType)) {
            if (!StringUtils.hasText(text(ref.get("serviceBean")))) {
                issues.add(error("call.service.bean.required", path + ".ref.serviceBean", "Service call requires serviceBean."));
            }
            if (!StringUtils.hasText(text(ref.get("methodName")))) {
                issues.add(error("call.service.method.required", path + ".ref.methodName", "Service call requires methodName."));
            }
        } else if (!StringUtils.hasText(text(ref.get("url")))) {
            issues.add(error("call.http.url.required", path + ".ref.url", "HTTP call requires url."));
        }

        List<Map<String, Object>> args = asMapList(call.get("args"));
        for (int index = 0; index < args.size(); index += 1) {
            Map<String, Object> arg = args.get(index);
            Map<String, Object> value = castMap(arg.get("value"));
            if (value.isEmpty()) {
                issues.add(error("call.arg.value.required", path + ".args[" + index + "].value", "Call argument value is required."));
                continue;
            }
            issues.addAll(validateValue(value, path + ".args[" + index + "].value", null, validationContext));
        }
        return issues;
    }

    private List<ParamAssemblerIssueResponse> validateObject(Map<String, Object> object,
                                                             String path,
                                                             ParamAssemblerArgMeta argMeta,
                                                             ValidationContext validationContext) {
        List<ParamAssemblerIssueResponse> issues = new ArrayList<>();
        List<Map<String, Object>> fields = asMapList(object.get("fields"));
        if (fields.isEmpty()) {
            if (argMeta != null && Boolean.TRUE.equals(argMeta.getRequired())) {
                issues.add(error("object.fields.required", path + ".fields", "Object value requires at least one field mapping."));
            }
            return issues;
        }

        Set<String> duplicateCheck = new LinkedHashSet<>();
        Set<String> schemaLeaves = flattenLeafPaths(castMap(argMeta != null ? argMeta.getSchema() : null)).stream()
                .map(this::normalizeLeaf)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (int index = 0; index < fields.size(); index += 1) {
            Map<String, Object> field = fields.get(index);
            String fieldPath = text(field.get("path"));
            String normalizedFieldPath = normalizeLeaf(fieldPath);
            if (!StringUtils.hasText(normalizedFieldPath)) {
                issues.add(error("object.field.path.required", path + ".fields[" + index + "].path", "Object field path is required."));
                continue;
            }
            if (!duplicateCheck.add(normalizedFieldPath)) {
                issues.add(error("object.field.duplicate", path + ".fields[" + index + "].path", "Duplicated object field path: " + fieldPath));
            }
            if (!schemaLeaves.isEmpty() && !schemaLeaves.contains(normalizedFieldPath)) {
                issues.add(warning("object.field.unknown", path + ".fields[" + index + "].path", "Field is not present in current schema: " + fieldPath));
            }
            issues.addAll(validateValue(castMap(field.get("value")), path + ".fields[" + index + "].value", null, validationContext));
        }

        Map<String, Object> schema = castMap(argMeta != null ? argMeta.getSchema() : null);
        Set<String> requiredLeafs = collectRequiredLeafPaths(schema);
        if (!requiredLeafs.isEmpty()) {
            Set<String> configuredLeafs = collectConfiguredObjectPaths(object);
            for (String requiredLeaf : requiredLeafs) {
                if (!configuredLeafs.contains(requiredLeaf)) {
                    issues.add(error("object.field.required", path + ".fields", "Missing required field mapping: " + requiredLeaf));
                }
            }
        }
        return issues;
    }

    private List<ParamAssemblerIssueResponse> validateList(Map<String, Object> list,
                                                           String path,
                                                           ParamAssemblerArgMeta argMeta,
                                                           ValidationContext validationContext) {
        List<ParamAssemblerIssueResponse> issues = new ArrayList<>();
        Map<String, Object> source = castMap(list.get("source"));
        if (source.isEmpty()) {
            issues.add(error("list.source.required", path + ".source", "List node requires source."));
        } else {
            String sourceKind = text(source.get("kind")).toLowerCase(Locale.ROOT);
            if ("call".equals(sourceKind)) {
                issues.addAll(validateCall(source, path + ".source", validationContext));
            } else if ("source".equals(sourceKind) || !StringUtils.hasText(sourceKind)) {
                issues.addAll(validateSource(source, path + ".source", validationContext));
            } else {
                issues.add(error("list.source.kind.unsupported", path + ".source.kind", "List source only supports source or call."));
            }
        }

        List<Map<String, Object>> ops = asMapList(list.get("ops"));
        for (int index = 0; index < ops.size(); index += 1) {
            String op = text(ops.get(index).get("op"));
            if (!LIST_OPS.contains(op)) {
                issues.add(error("list.op.unsupported", path + ".ops[" + index + "].op", "Phase 1 only supports filter/map/compose."));
            }
        }

        Map<String, Object> item = castMap(list.get("item"));
        if (!item.isEmpty()) {
            String itemKind = text(item.get("kind"));
            if ("object".equalsIgnoreCase(itemKind)) {
                ParamAssemblerArgMeta itemMeta = new ParamAssemblerArgMeta();
                itemMeta.setName(firstNonBlank(text(argMeta != null ? argMeta.getName() : null), "item"));
                itemMeta.setRequired(Boolean.FALSE);
                itemMeta.setSchema(extractItemSchema(castMap(argMeta != null ? argMeta.getSchema() : null)));
                issues.addAll(validateObject(item, path + ".item", itemMeta, validationContext));
            } else {
                issues.addAll(validateValue(item, path + ".item", null, validationContext));
            }
        }

        if (argMeta != null) {
            Map<String, Object> itemSchema = extractItemSchema(castMap(argMeta.getSchema()));
            if (isListType("", itemSchema)) {
                issues.add(warning("list.nested.unsupported", path, "Phase 1 does not support full visual assembly for nested arrays or collections."));
            }
        }
        return issues;
    }

    private Map<String, Integer> buildTempOrderByKey(List<Map<String, Object>> temps,
                                                     List<ParamAssemblerIssueResponse> issues) {
        Map<String, Integer> tempOrderByKey = new LinkedHashMap<>();
        for (int index = 0; index < temps.size(); index += 1) {
            String key = text(temps.get(index).get("key"));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Integer previousIndex = tempOrderByKey.putIfAbsent(key, index);
            if (previousIndex != null) {
                issues.add(error("temp.key.duplicate", "temps[" + index + "].key", "Duplicated temp key: " + key));
            }
        }
        return tempOrderByKey;
    }

    private List<ParamAssemblerIssueResponse> validateTempCycles(List<Map<String, Object>> temps,
                                                                 Map<String, Integer> tempOrderByKey) {
        if (tempOrderByKey.isEmpty()) {
            return List.of();
        }

        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (String key : tempOrderByKey.keySet()) {
            graph.put(key, new LinkedHashSet<>());
        }

        for (int index = 0; index < temps.size(); index += 1) {
            Map<String, Object> temp = temps.get(index);
            String key = text(temp.get("key"));
            if (!StringUtils.hasText(key) || !Objects.equals(tempOrderByKey.get(key), index)) {
                continue;
            }
            List<TempReference> refs = new ArrayList<>();
            collectTempReferences(castMap(temp.get("value")), "temps[" + index + "].value", refs);
            for (TempReference ref : refs) {
                if (tempOrderByKey.containsKey(ref.targetKey())) {
                    graph.get(key).add(ref.targetKey());
                }
            }
        }

        List<ParamAssemblerIssueResponse> issues = new ArrayList<>();
        for (List<String> cycle : findTempCycles(graph)) {
            if (cycle.size() <= 2) {
                continue;
            }
            String cyclePath = String.join(" -> ", cycle);
            Set<String> seen = new HashSet<>();
            for (String key : cycle) {
                if (!seen.add(key)) {
                    continue;
                }
                Integer index = tempOrderByKey.get(key);
                if (index != null) {
                    issues.add(error("temp.ref.cycle", "temps[" + index + "].value", "Temp cycle detected: " + cyclePath));
                }
            }
        }
        return issues;
    }

    private void collectTempReferences(Map<String, Object> value,
                                       String path,
                                       Collection<TempReference> refs) {
        if (value == null || value.isEmpty()) {
            return;
        }

        String kind = text(value.get("kind")).toLowerCase(Locale.ROOT);
        switch (kind) {
            case "source" -> {
                if ("temp".equals(text(value.get("sourceType")))) {
                    String tempKey = extractTempKey(text(value.get("path")));
                    if (StringUtils.hasText(tempKey)) {
                        refs.add(new TempReference(tempKey, path + ".path"));
                    }
                }
            }
            case "call" -> {
                List<Map<String, Object>> args = asMapList(value.get("args"));
                for (int index = 0; index < args.size(); index += 1) {
                    collectTempReferences(castMap(args.get(index).get("value")), path + ".args[" + index + "].value", refs);
                }
            }
            case "object" -> {
                List<Map<String, Object>> fields = asMapList(value.get("fields"));
                for (int index = 0; index < fields.size(); index += 1) {
                    collectTempReferences(castMap(fields.get(index).get("value")), path + ".fields[" + index + "].value", refs);
                }
            }
            case "list" -> {
                collectTempReferences(castMap(value.get("source")), path + ".source", refs);
                collectTempReferences(castMap(value.get("item")), path + ".item", refs);
                List<Map<String, Object>> ops = asMapList(value.get("ops"));
                for (int index = 0; index < ops.size(); index += 1) {
                    Map<String, Object> op = ops.get(index);
                    collectTempReferences(castMap(op.get("value")), path + ".ops[" + index + "].value", refs);
                    List<Map<String, Object>> fields = asMapList(op.get("fields"));
                    for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex += 1) {
                        collectTempReferences(
                                castMap(fields.get(fieldIndex).get("source")),
                                path + ".ops[" + index + "].fields[" + fieldIndex + "].source",
                                refs);
                    }
                }
            }
            case "coalesce" -> {
                List<Map<String, Object>> candidates = asMapList(value.get("candidates"));
                for (int index = 0; index < candidates.size(); index += 1) {
                    collectTempReferences(candidates.get(index), path + ".candidates[" + index + "]", refs);
                }
            }
            default -> {
            }
        }
    }

    private List<List<String>> findTempCycles(Map<String, Set<String>> graph) {
        List<List<String>> cycles = new ArrayList<>();
        Map<String, Integer> state = new HashMap<>();
        List<String> stack = new ArrayList<>();
        Set<String> seenCycles = new LinkedHashSet<>();

        for (String node : graph.keySet()) {
            if (state.getOrDefault(node, 0) == 0) {
                dfsTempCycle(node, graph, state, stack, seenCycles, cycles);
            }
        }
        return cycles;
    }

    private void dfsTempCycle(String node,
                              Map<String, Set<String>> graph,
                              Map<String, Integer> state,
                              List<String> stack,
                              Set<String> seenCycles,
                              List<List<String>> cycles) {
        state.put(node, 1);
        stack.add(node);

        for (String next : graph.getOrDefault(node, Set.of())) {
            if (!graph.containsKey(next)) {
                continue;
            }
            int nextState = state.getOrDefault(next, 0);
            if (nextState == 0) {
                dfsTempCycle(next, graph, state, stack, seenCycles, cycles);
                continue;
            }
            if (nextState == 1) {
                int start = stack.indexOf(next);
                if (start >= 0) {
                    List<String> cycle = new ArrayList<>(stack.subList(start, stack.size()));
                    cycle.add(next);
                    String signature = canonicalCycleSignature(cycle);
                    if (seenCycles.add(signature)) {
                        cycles.add(cycle);
                    }
                }
            }
        }

        stack.remove(stack.size() - 1);
        state.put(node, 2);
    }

    private String canonicalCycleSignature(List<String> cycle) {
        if (cycle.size() <= 1) {
            return String.join("->", cycle);
        }
        List<String> nodes = new ArrayList<>(cycle.subList(0, cycle.size() - 1));
        String best = null;
        for (int index = 0; index < nodes.size(); index += 1) {
            List<String> rotated = new ArrayList<>();
            for (int offset = 0; offset < nodes.size(); offset += 1) {
                rotated.add(nodes.get((index + offset) % nodes.size()));
            }
            String candidate = String.join("->", rotated);
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        return best == null ? "" : best;
    }

    private String extractTempKey(String path) {
        return stripPrefix(path, "temp.");
    }

    private boolean isConfiguredValue(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String kind = text(value.get("kind")).toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "source" -> StringUtils.hasText(text(value.get("path"))) || value.containsKey("constValue");
            case "call" -> {
                Map<String, Object> ref = castMap(value.get("ref"));
                yield !ref.isEmpty();
            }
            case "object" -> !collectConfiguredObjectPaths(value).isEmpty();
            case "list" -> isConfiguredList(value);
            case "expr", "expression" -> StringUtils.hasText(firstNonBlank(text(value.get("expression")), text(value.get("value"))));
            case "coalesce" -> asMapList(value.get("candidates")).stream().anyMatch(this::isConfiguredValue);
            default -> false;
        };
    }

    private boolean isConfiguredList(Map<String, Object> list) {
        Map<String, Object> source = castMap(list.get("source"));
        if (!isConfiguredValue(source)) {
            return false;
        }
        Map<String, Object> item = castMap(list.get("item"));
        return item.isEmpty() || isConfiguredValue(item);
    }

    private Set<String> collectConfiguredObjectPaths(Map<String, Object> object) {
        Set<String> configured = new LinkedHashSet<>();
        for (Map<String, Object> field : asMapList(object.get("fields"))) {
            String fieldPath = normalizeLeaf(text(field.get("path")));
            if (!StringUtils.hasText(fieldPath)) {
                continue;
            }
            Map<String, Object> value = castMap(field.get("value"));
            if (isConfiguredValue(value)) {
                configured.add(fieldPath);
            }
            if ("object".equalsIgnoreCase(text(value.get("kind")))) {
                for (String child : collectConfiguredObjectPaths(value)) {
                    configured.add(fieldPath + "." + child);
                }
            }
        }
        return configured;
    }

    private Set<String> collectRequiredLeafPaths(Map<String, Object> schema) {
        Set<String> output = new LinkedHashSet<>();
        flattenRequiredLeafPaths(schema, "", output);
        return output;
    }

    private void flattenRequiredLeafPaths(Map<String, Object> schema, String prefix, Set<String> output) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Map<String, Object> properties = castMap(schema.get("properties"));
        if (properties.isEmpty()) {
            if (StringUtils.hasText(prefix)) {
                output.add(normalizeLeaf(prefix));
            }
            return;
        }

        Set<String> required = asStringSet(schema.get("required"));
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = text(entry.getKey());
            Map<String, Object> child = castMap(entry.getValue());
            String childPath = StringUtils.hasText(prefix) ? prefix + "." + key : key;
            boolean childRequired = required.contains(key) || Boolean.TRUE.equals(child.get("x-required"));
            if (!childRequired && castMap(child.get("properties")).isEmpty()) {
                continue;
            }

            if (isListType("", child)) {
                Map<String, Object> itemSchema = extractItemSchema(child);
                if (isObjectType("", itemSchema)) {
                    flattenRequiredLeafPaths(itemSchema, childPath + "[]", output);
                } else if (childRequired) {
                    output.add(normalizeLeaf(childPath + "[]"));
                }
                continue;
            }

            if (isObjectType("", child)) {
                int before = output.size();
                flattenRequiredLeafPaths(child, childPath, output);
                if (before == output.size() && childRequired) {
                    output.add(normalizeLeaf(childPath));
                }
                continue;
            }

            if (childRequired) {
                output.add(normalizeLeaf(childPath));
            }
        }
    }

    private List<String> flattenLeafPaths(Map<String, Object> schema) {
        List<String> output = new ArrayList<>();
        flattenLeafPaths(schema, "", output);
        return output.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeLeaf)
                .distinct()
                .collect(Collectors.toList());
    }

    private void flattenLeafPaths(Map<String, Object> schema, String prefix, List<String> output) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Map<String, Object> properties = castMap(schema.get("properties"));
        if (properties.isEmpty()) {
            if (StringUtils.hasText(prefix)) {
                output.add(prefix);
            }
            return;
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = text(entry.getKey());
            Map<String, Object> child = castMap(entry.getValue());
            String childPath = StringUtils.hasText(prefix) ? prefix + "." + key : key;

            if (isListType("", child)) {
                Map<String, Object> itemSchema = extractItemSchema(child);
                if (isObjectType("", itemSchema)) {
                    flattenLeafPaths(itemSchema, childPath + "[]", output);
                } else {
                    output.add(childPath + "[]");
                }
                continue;
            }

            if (isObjectType("", child)) {
                flattenLeafPaths(child, childPath, output);
                continue;
            }
            output.add(childPath);
        }
    }

    private Map<String, Object> extractItemSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return Map.of();
        }
        return castMap(schema.get("items"));
    }

    private boolean isObjectType(String javaType, Map<String, Object> schema) {
        if (schema != null && !schema.isEmpty()) {
            if ("object".equalsIgnoreCase(text(schema.get("type")))) {
                return true;
            }
            if (!castMap(schema.get("properties")).isEmpty()) {
                return true;
            }
        }

        String type = text(javaType).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(type) || isListType(javaType, schema)) {
            return false;
        }
        return !Set.of(
                "string", "java.lang.string",
                "boolean", "java.lang.boolean",
                "int", "integer", "java.lang.integer",
                "long", "java.lang.long",
                "double", "java.lang.double",
                "float", "java.lang.float",
                "short", "java.lang.short",
                "byte", "java.lang.byte",
                "char", "character", "java.lang.character",
                "number", "java.lang.number"
        ).contains(type);
    }

    private boolean isListType(String javaType, Map<String, Object> schema) {
        if (schema != null && !schema.isEmpty()) {
            if ("array".equalsIgnoreCase(text(schema.get("type")))) {
                return true;
            }
            if (!castMap(schema.get("items")).isEmpty()) {
                return true;
            }
        }

        String type = text(javaType).toLowerCase(Locale.ROOT);
        return type.endsWith("[]")
                || type.startsWith("java.util.list")
                || type.startsWith("list<")
                || type.startsWith("java.util.set")
                || type.startsWith("set<")
                || type.startsWith("java.util.collection")
                || type.startsWith("collection<")
                || "array".equals(type);
    }

    private String findBestSourcePath(String leaf, List<String> sourcePaths) {
        String normalizedLeaf = normalizeLeaf(leaf);
        if (!StringUtils.hasText(normalizedLeaf) || sourcePaths == null || sourcePaths.isEmpty()) {
            return "";
        }

        String bestCandidate = "";
        int bestScore = Integer.MIN_VALUE;
        for (String candidate : sourcePaths) {
            int score = scoreCandidate(normalizedLeaf, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }
        return bestScore > 0 ? bestCandidate : "";
    }

    private int scoreCandidate(String normalizedLeaf, String candidate) {
        String normalizedCandidate = normalizeLeaf(candidate);
        if (!StringUtils.hasText(normalizedLeaf) || !StringUtils.hasText(normalizedCandidate)) {
            return Integer.MIN_VALUE;
        }
        if (normalizedLeaf.equals(normalizedCandidate)) {
            return 200;
        }
        if (normalizedCandidate.endsWith("." + normalizedLeaf)) {
            return 180;
        }
        if (normalizedLeaf.endsWith("." + normalizedCandidate)) {
            return 120;
        }

        String[] leafParts = normalizedLeaf.split("\\.");
        String[] candidateParts = normalizedCandidate.split("\\.");
        String leafTail = leafParts[leafParts.length - 1];
        String candidateTail = candidateParts[candidateParts.length - 1];

        int score = 0;
        if (leafTail.equals(candidateTail)) {
            score += 80;
        }
        if (normalizedCandidate.contains("." + leafTail) || normalizedCandidate.startsWith(leafTail + ".")) {
            score += 20;
        }
        int sharedSuffix = 0;
        for (int i = 1; i <= Math.min(leafParts.length, candidateParts.length); i += 1) {
            if (!leafParts[leafParts.length - i].equals(candidateParts[candidateParts.length - i])) {
                break;
            }
            sharedSuffix += 1;
        }
        score += sharedSuffix * 12;

        if (candidate.startsWith("request.body.")) {
            score += 5;
        }
        return score;
    }

    private String normalizeLeaf(String value) {
        String normalized = text(value)
                .replace("$.", "")
                .replace("[]", "")
                .replace("[*]", "");
        String[] prefixes = {
                "request.body.",
                "request.query.",
                "request.path.",
                "request.headers.",
                "request.",
                "context.",
                "temp.",
                "nodeOutput.",
                "item."
        };
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length());
                break;
            }
        }
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private boolean isSuggestAllMode(String mode) {
        return "all".equalsIgnoreCase(text(mode));
    }

    private List<String> buildTempSourcePaths(Map<String, Object> ast) {
        return asMapList(ast.get("temps")).stream()
                .map(item -> text(item.get("key")))
                .filter(StringUtils::hasText)
                .map(key -> "temp." + key)
                .collect(Collectors.toList());
    }

    private String buildSuggestSummary(SuggestStats stats, String mode) {
        String action = isSuggestAllMode(mode) ? "已重算推荐映射" : "已自动补齐空白映射";
        if (stats.updatedCount() <= 0) {
            return action + "，当前未找到可更新的推荐项。";
        }
        return action + "，共更新 " + stats.updatedCount() + " 处配置，覆盖 "
                + stats.touchedArgs().size() + " 个参数。";
    }

    private List<String> normalizeSourcePaths(List<String> sourcePaths) {
        if (sourcePaths == null || sourcePaths.isEmpty()) {
            return List.of();
        }
        return sourcePaths.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<ParamAssemblerArgMeta> safeArgs(List<ParamAssemblerArgMeta> args) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }
        return args.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> asMapList(Object raw) {
        if (!(raw instanceof Collection<?> collection) || collection.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> output = new ArrayList<>();
        for (Object item : collection) {
            Map<String, Object> casted = castMap(item);
            if (!casted.isEmpty()) {
                output.add(casted);
            }
        }
        return output;
    }

    private Set<String> asStringSet(Object raw) {
        if (!(raw instanceof Collection<?> collection) || collection.isEmpty()) {
            return Set.of();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Map<String, Object>> jsonMapList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        return asMapList(OBJECT_MAPPER.convertValue(node, new TypeReference<List<Map<String, Object>>>() {}));
    }

    private Map<String, Object> jsonMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return castMap(OBJECT_MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {}));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            output.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return output;
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.convertValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private List<Map<String, Object>> deepCopyList(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        return OBJECT_MAPPER.convertValue(raw, new TypeReference<List<Map<String, Object>>>() {});
    }

    private Object deepCopyMapOrValue(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return deepCopyMap(castMap(map));
        }
        if (raw instanceof Collection<?> collection) {
            return OBJECT_MAPPER.convertValue(collection, new TypeReference<List<Object>>() {});
        }
        return raw;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private ParamAssemblerIssueResponse error(String code, String path, String message) {
        return new ParamAssemblerIssueResponse(code, path, "error", message);
    }

    private ParamAssemblerIssueResponse warning(String code, String path, String message) {
        return new ParamAssemblerIssueResponse(code, path, "warning", message);
    }

    private record SuggestStats(int updatedCount, List<String> touchedArgs) {
    }
}

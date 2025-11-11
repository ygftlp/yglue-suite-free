package org.yglue.flow.orch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yglue.flow.orch.domain.ProjectMetadata;
import org.yglue.flow.orch.persistence.mapper.ProjectMetadataMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProjectService projectService;
    private final ProjectMetadataMapper metadataMapper;
    private final ProjectEndpointService projectEndpointService;
    private final FlowModelService flowModelService;
    private final FlowResolverService flowResolverService;

    public MetadataService(ProjectService projectService,
                           ProjectMetadataMapper metadataMapper,
                           ProjectEndpointService projectEndpointService,
                           FlowModelService flowModelService,
                           FlowResolverService flowResolverService) {
        this.projectService = projectService;
        this.metadataMapper = metadataMapper;
        this.projectEndpointService = projectEndpointService;
        this.flowModelService = flowModelService;
        this.flowResolverService = flowResolverService;
    }

    @Transactional
    public SaveResult save(String projectKey, String optionalName, String contentJson) {
        ProjectService.EnsureResult ensure = projectService.ensureProjectWithFlag(projectKey, optionalName);
        ProjectMetadata metadata = new ProjectMetadata();
        metadata.setProjectId(ensure.project().getId());
        metadata.setContentJson(contentJson);
        metadataMapper.insert(metadata);

        JsonNode root = readRoot(contentJson);
        List<ProjectEndpointService.EndpointPayload> endpoints = new ArrayList<>();
        if (root != null) {
            endpoints.addAll(parseRestEndpoints(root));
            endpoints.addAll(parseFlowEndpoints(root));
            flowModelService.syncModels(
                    ensure.project().getId(),
                    parseFlowModels(root)
            );
            flowResolverService.syncResolvers(
                    ensure.project().getId(),
                    parseFlowResolvers(root)
            );
        }

        projectEndpointService.syncEndpoints(
                ensure.project().getId(),
                endpoints
        );

        return new SaveResult(metadata, ensure.created());
    }

    public List<ProjectMetadata> list(String projectKey) {
        var project = projectService.requireProject(projectKey);
        return metadataMapper.selectByProject(project.getId());
    }

    private JsonNode readRoot(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(contentJson);
        } catch (Exception ex) {
            log.warn("Failed to parse metadata JSON", ex);
            return null;
        }
    }

    private List<ProjectEndpointService.EndpointPayload> parseRestEndpoints(JsonNode root) {
        List<ProjectEndpointService.EndpointPayload> endpoints = new ArrayList<>();
        if (root == null || root.isMissingNode()) {
            return endpoints;
        }
        JsonNode restArray = root.path("rests");
        if (!restArray.isArray()) {
            return endpoints;
        }
        for (JsonNode node : restArray) {
            String httpMethod = textOrDefault(node, "httpMethod", "GET").toUpperCase();
            String path = textOrDefault(node, "path", "/");
            String className = textOrDefault(node, "class", "");
            String methodName = textOrDefault(node, "method", "");
            String fallbackDisplay;
            if (!className.isBlank() && !methodName.isBlank()) {
                fallbackDisplay = className + "#" + methodName;
            } else if (!methodName.isBlank()) {
                fallbackDisplay = methodName;
            } else {
                fallbackDisplay = httpMethod + " " + path;
            }
            String fallbackDescription = httpMethod + " " + path;
            String nameOverride = textOrDefault(node, "name", "");
            if (nameOverride.isBlank()) {
                nameOverride = textOrDefault(node, "displayName", "");
            }
            String descOverride = textOrDefault(node, "description", "");
            String displayName = nameOverride.isBlank() ? fallbackDisplay : nameOverride;
            String description = descOverride.isBlank() ? fallbackDescription : descOverride;
            String raw = node.toString();

            endpoints.add(new ProjectEndpointService.EndpointPayload(
                    "REST",
                    "SYSTEM",
                    httpMethod,
                    path,
                    displayName,
                    description,
                    raw
            ));
        }
        return endpoints;
    }

    private List<ProjectEndpointService.EndpointPayload> parseFlowEndpoints(JsonNode root) {
        List<ProjectEndpointService.EndpointPayload> endpoints = new ArrayList<>();
        if (root == null || root.isMissingNode()) {
            return endpoints;
        }
        JsonNode apis = root.path("apis");
        if (!apis.isArray()) {
            return endpoints;
        }
        for (JsonNode apiNode : apis) {
            String apiClass = textOrDefault(apiNode, "class", "");
            String apiName = textOrDefault(apiNode, "name", apiClass);
            String apiVersion = textOrDefault(apiNode, "version", "");
            String apiDescription = textOrDefault(apiNode, "description", "");
            String apiDisplay = apiName.isBlank() ? apiClass : apiName;
            if (!apiVersion.isBlank()) {
                apiDisplay = apiDisplay + " v" + apiVersion;
            }
            if (apiDisplay.isBlank()) {
                apiDisplay = "Flow API";
            }
            String apiMethodKey = apiName.isBlank() ? apiClass : apiName;
            if (apiMethodKey.isBlank()) {
                apiMethodKey = "flow-api:" + Integer.toHexString(apiNode.toString().hashCode());
            }
            String apiPathKey = apiClass.isBlank()
                    ? apiMethodKey
                    : (apiVersion.isBlank() ? apiClass : apiClass + ":" + apiVersion);

            endpoints.add(new ProjectEndpointService.EndpointPayload(
                    "FLOW_API",
                    "BUSINESS",
                    apiMethodKey,
                    apiPathKey,
                    apiDisplay,
                    apiDescription.isBlank() ? ("Flow API " + apiDisplay) : apiDescription,
                    apiNode.toString()
            ));

            JsonNode operations = apiNode.path("operations");
            if (!operations.isArray()) {
                continue;
            }
            for (JsonNode opNode : operations) {
                String opName = textOrDefault(opNode, "name", "");
                String opMethod = textOrDefault(opNode, "method", "");
                String opDescription = textOrDefault(opNode, "description", "");
                String opDisplay = !opName.isBlank() ? opName : opMethod;
                if (opDisplay.isBlank()) {
                    opDisplay = "operation";
                }
                /**
                 * methodKey 应该始终使用实际的方法名（opMethod），而不是显示名称（opName）
                 * 因为运行时需要通过实际方法名来查找和调用方法
                 * opName 仅用于显示，不应该用于方法查找
                 */
                String opMethodKey = !opMethod.isBlank() ? opMethod : opName;
                if (opMethodKey.isBlank()) {
                    opMethodKey = "flow-operation:" + Integer.toHexString(opNode.toString().hashCode());
                }
                String opPathKey;
                if (!apiClass.isBlank() && !opMethod.isBlank()) {
                    opPathKey = apiClass + "#" + opMethod;
                } else if (!apiClass.isBlank()) {
                    opPathKey = apiClass + "#" + opMethodKey;
                } else {
                    opPathKey = opMethodKey;
                }
                String description = opDescription.isBlank() ? ("Flow Operation " + opDisplay) : opDescription;
                
                // 为 FlowOperation 的 configJson 添加所属 flowApi 的 bean 名称（Service name）
                // 优先使用插件导出的 flowApiBeanName（从 Spring 注解获取），如果没有则使用 flowApiName 或 apiName
                String opConfigJson = opNode.toString();
                try {
                    ObjectNode opConfig = (ObjectNode) OBJECT_MAPPER.readTree(opConfigJson);
                    // 优先使用插件导出的 flowApiBeanName（从 @Service/@Component 获取的 bean 名称）
                    String flowApiBeanName = null;
                    if (opConfig.has("flowApiBeanName") && !opConfig.get("flowApiBeanName").asText().isBlank()) {
                        flowApiBeanName = opConfig.get("flowApiBeanName").asText();
                    } else if (opConfig.has("flowApiName") && !opConfig.get("flowApiName").asText().isBlank()) {
                        // 回退到使用 flowApiName
                        flowApiBeanName = opConfig.get("flowApiName").asText();
                    } else {
                        // 最后使用当前解析的 apiName
                        flowApiBeanName = apiName;
                    }
                    String flowApiName = opConfig.has("flowApiName") && !opConfig.get("flowApiName").asText().isBlank()
                            ? opConfig.get("flowApiName").asText()
                            : apiName;
                    String flowApiClassValue = opConfig.has("flowApiClass") && !opConfig.get("flowApiClass").asText().isBlank()
                            ? opConfig.get("flowApiClass").asText()
                            : apiClass;
                    // 确保 flowApiBeanName、flowApiName 和 flowApiClass 字段存在
                    opConfig.put("flowApiBeanName", flowApiBeanName);
                    opConfig.put("flowApiName", flowApiName);
                    opConfig.put("flowApiClass", flowApiClassValue);
                    opConfigJson = OBJECT_MAPPER.writeValueAsString(opConfig);
                } catch (Exception e) {
                    // 如果解析失败，使用原始的 configJson
                    log.warn("Failed to enrich FlowOperation configJson with flowApi bean name: {}", e.getMessage());
                }

                endpoints.add(new ProjectEndpointService.EndpointPayload(
                        "FLOW_OPERATION",
                        "BUSINESS",
                        opMethodKey,
                        opPathKey,
                        opDisplay,
                        description,
                        opConfigJson
                ));
            }
        }
        return endpoints;
    }

    private List<FlowModelService.ModelPayload> parseFlowModels(JsonNode root) {
        List<FlowModelService.ModelPayload> models = new ArrayList<>();
        if (root == null || root.isMissingNode()) {
            return models;
        }
        JsonNode modelArray = root.path("models");
        if (!modelArray.isArray()) {
            return models;
        }
        for (JsonNode node : modelArray) {
            String identifier = textOrDefault(node, "id", "");
            if (identifier.isBlank()) {
                identifier = textOrDefault(node, "name", "");
            }
            if (identifier.isBlank()) {
                continue;
            }
            String name = textOrDefault(node, "name", identifier);
            String className = textOrDefault(node, "class", "");
            String description = textOrDefault(node, "description", "");
            String category = textOrDefault(node, "category", "");
            String version = textOrDefault(node, "version", "1.0.0");
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = node.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode tagNode : tagsNode) {
                    if (tagNode != null && !tagNode.isNull()) {
                        String tagValue = tagNode.asText();
                        if (tagValue != null && !tagValue.isBlank()) {
                            tags.add(tagValue);
                        }
                    }
                }
            }
            String schemaJson = null;
            JsonNode schemaNode = node.get("schema");
            if (schemaNode != null && !schemaNode.isNull()) {
                schemaJson = schemaNode.toString();
            }
            models.add(new FlowModelService.ModelPayload(
                    identifier,
                    name,
                    className,
                    description,
                    category,
                    version,
                    tags,
                    schemaJson,
                    node.toString()
            ));
        }
        return models;
    }

    private List<FlowResolverService.ResolverPayload> parseFlowResolvers(JsonNode root) {
        List<FlowResolverService.ResolverPayload> resolvers = new ArrayList<>();
        if (root == null || root.isMissingNode()) {
            return resolvers;
        }
        JsonNode resolverArray = root.path("resolvers");
        if (!resolverArray.isArray()) {
            return resolvers;
        }
        for (JsonNode node : resolverArray) {
            if (node == null || node.isNull() || !node.isObject()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            FlowResolverService.ResolverPayload payload =
                    FlowResolverService.ResolverPayload.fromRaw(OBJECT_MAPPER.convertValue(node, java.util.Map.class));
            if (payload != null) {
                resolvers.add(payload);
            }
        }
        return resolvers;
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.asText().isBlank()) {
            return fallback;
        }
        return child.asText();
    }

    public record SaveResult(ProjectMetadata metadata, boolean projectCreated) {}
}


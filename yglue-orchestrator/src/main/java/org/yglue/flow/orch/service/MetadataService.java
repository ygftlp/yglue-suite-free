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
        JsonNode services = root.path("services");
        if (!services.isArray()) {
            return endpoints;
        }
        for (JsonNode serviceNode : services) {
            String serviceClass = textOrDefault(serviceNode, "class", "");
            String serviceName = textOrDefault(serviceNode, "name", serviceClass);
            String bean = textOrDefault(serviceNode, "bean", "");
            String serviceVersion = textOrDefault(serviceNode, "version", "");
            String serviceDescription = textOrDefault(serviceNode, "description", "");
            String serviceDisplay = serviceName.isBlank() ? serviceClass : serviceName;
            if (!serviceVersion.isBlank()) {
                serviceDisplay = serviceDisplay + " v" + serviceVersion;
            }
            if (serviceDisplay.isBlank()) {
                serviceDisplay = "Flow Service";
            }
            String serviceMethodKey = serviceName.isBlank() ? serviceClass : serviceName;
            if (serviceMethodKey.isBlank()) {
                serviceMethodKey = "flow-service:" + Integer.toHexString(serviceNode.toString().hashCode());
            }
            String servicePathKey = serviceClass.isBlank()
                    ? serviceMethodKey
                    : (serviceVersion.isBlank() ? serviceClass : serviceClass + ":" + serviceVersion);

            // 只创建 SERVICE 记录，operations 信息包含在 configJson 中
            // 前端可以从 SERVICE 的 configJson 中解析 operations
            endpoints.add(new ProjectEndpointService.EndpointPayload(
                    "SERVICE",
                    "BUSINESS",
                    serviceMethodKey,
                    servicePathKey,
                    serviceDisplay,
                    serviceDescription.isBlank() ? ("Flow Service " + serviceDisplay) : serviceDescription,
                    serviceNode.toString()
            ));
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


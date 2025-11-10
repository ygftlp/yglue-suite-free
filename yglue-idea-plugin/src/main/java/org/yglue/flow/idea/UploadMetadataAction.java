package org.yglue.flow.idea;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.json.JSONObject;
import org.yglue.flow.idea.settings.YgflowSettingsConfigurable;
import org.yglue.flow.idea.settings.YgflowSettingsState;
import org.yglue.flow.idea.util.YgflowConfigurationResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class UploadMetadataAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(UploadMetadataAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        upload(project, true);
    }

    public static boolean upload(Project project, boolean showDialogs) {
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        if (settings == null) {
            handleError(project, "yglue settings service is unavailable.", showDialogs);
            return false;
        }
        try {
            String basePath = project.getBasePath();
            if (basePath == null) {
                handleError(project, "Project basePath is null", showDialogs);
                return false;
            }

            /**
             * 自动上报时总是先重新导出最新的元数据，确保上报的是最新内容
             * 手动上报时，如果 export.json 不存在才自动导出
             */
            Path json = Path.of(basePath, ".ygflow", "export.json");
            boolean autoExported = false;
            boolean shouldReexport = !showDialogs; // 自动上报（showDialogs=false）时总是重新导出
            
            if (shouldReexport || !Files.exists(json)) {
                try {
                    json = ExportMetadataAction.exportProject(project);
                    autoExported = true;
                } catch (Exception exportEx) {
                    handleError(project, "Metadata export failed: " + exportEx.getMessage(), showDialogs);
                    return false;
                }
            }
            String content = Files.readString(json);

            String endpoint = trimTrailingSlash(defaultBaseUrl(settings.baseUrl));

            YgflowConfigurationResolver.ResolvedValue resolvedProject = showDialogs
                    ? YgflowConfigurationResolver.resolveProjectKey(project, settings)
                    : YgflowConfigurationResolver.resolveProjectKey(project, settings, false);
            String projectKey = resolvedProject.value();

            if (endpoint.isBlank() || projectKey.isBlank()) {
                if (showDialogs) {
                    Messages.showInfoMessage(project,
                    "Please configure yglue endpoint and project key before uploading.",
                    "yglue Upload");
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, YgflowSettingsConfigurable.class);
                } else {
                    LOG.info("Skipping metadata upload: endpoint or project key not configured.");
                }
                return false;
            }

            String instanceKey = settings.instanceKey;

            JSONObject body = new JSONObject();
            body.put("contentJson", content);
            body.put("projectName", project.getName());

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/projects/" + projectKey + "/metadata"))
                    .header("Content-Type", "application/json")
                    .header("X-YGlue-Instance", instanceKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                boolean projectCreated = false;
                try {
                    JSONObject responseJson = new JSONObject(resp.body());
                    projectCreated = responseJson.optBoolean("projectCreated", false);
                } catch (Exception ignored) {
                }
                String message = "Uploaded to " + endpoint + " for project " + projectKey;
                if (autoExported) {
                    message = "Metadata exported and " + message;
                }
                if (projectCreated) {
                    message += " (project created)";
                }
                handleInfo(project, message, showDialogs);
                return true;
            } else {
                handleError(project, "Upload failed: HTTP " + resp.statusCode() + " - " + resp.body(), showDialogs);
                return false;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            handleError(project, "Upload interrupted: " + ex.getMessage(), showDialogs);
            return false;
        } catch (Exception ex) {
            handleError(project, "Upload failed: " + ex.getMessage(), showDialogs);
            return false;
        }
    }

    private static String trimTrailingSlash(String value) {
        String v = value;
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String defaultBaseUrl(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "http://localhost:8090";
    }

    private static void handleInfo(Project project, String message, boolean showDialogs) {
        if (showDialogs) {
            Messages.showInfoMessage(project, message, "yglue Upload");
        } else {
            LOG.info(message);
        }
    }

    private static void handleError(Project project, String message, boolean showDialogs) {
        if (showDialogs) {
            Messages.showErrorDialog(project, message, "yglue Upload");
        } else {
            LOG.warn(message);
        }
    }
}

package org.yglue.flow.idea.snapshot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.yglue.flow.idea.settings.YgflowSettingsConfigurable;
import org.yglue.flow.idea.settings.YgflowSettingsState;

/**
 * 上传代码语义快照。
 */
public class UploadCodeSnapshotAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(UploadCodeSnapshotAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        upload(project, true);
    }

    public static boolean upload(@NotNull Project project, boolean showDialogs) {
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        if (settings == null) {
            handleError(project, "无法读取 yglue 配置。", showDialogs);
            return false;
        }
        if (!settings.isConfigured()) {
            promptConfigure(project, showDialogs);
            return false;
        }
        try {
            JSONObject snapshot = CodeSnapshotExporter.buildSnapshot(project, settings);
            CodeSnapshotExporter.writeSnapshot(project, snapshot);

            String endpoint = trimTrailingSlash(defaultBaseUrl(settings.baseUrl));
            if (endpoint.isBlank()) {
                promptConfigure(project, showDialogs);
                return false;
            }
            String projectKey = settings.projectKey;
            if (projectKey == null || projectKey.isBlank()) {
                promptConfigure(project, showDialogs);
                return false;
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/projects/" + projectKey + "/code-snapshots"))
                    .header("Content-Type", "application/json")
                    .header("X-YGlue-Instance", settings.instanceKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(snapshot.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                handleInfo(project, "代码语义快照已上传。", showDialogs);
                return true;
            }
            handleError(project, "上传失败：HTTP " + response.statusCode() + " - " + response.body(), showDialogs);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            handleError(project, "上传被中断：" + ex.getMessage(), showDialogs);
            return false;
        } catch (Exception ex) {
            handleError(project, "上传失败：" + ex.getMessage(), showDialogs);
            return false;
        }
    }

    private static void promptConfigure(Project project, boolean showDialogs) {
        if (showDialogs) {
            Messages.showInfoMessage(project, "请在 Settings 中配置 yglue 服务地址与项目标识。", "yglue");
            ShowSettingsUtil.getInstance().showSettingsDialog(project, YgflowSettingsConfigurable.class);
        } else {
            LOG.info("yglue settings incomplete, skip code snapshot upload.");
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
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
            Messages.showInfoMessage(project, message, "yglue");
        } else {
            LOG.info(message);
        }
    }

    private static void handleError(Project project, String message, boolean showDialogs) {
        if (showDialogs) {
            Messages.showErrorDialog(project, message, "yglue");
        } else {
            LOG.warn(message);
        }
    }
}

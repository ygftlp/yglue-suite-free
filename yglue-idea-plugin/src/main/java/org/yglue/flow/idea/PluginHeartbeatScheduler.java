package org.yglue.flow.idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.idea.settings.YgflowSettingsState;
import org.yglue.flow.idea.util.YgflowConfigurationResolver;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service(Service.Level.PROJECT)
public final class PluginHeartbeatScheduler implements Disposable {

    private static final Logger LOG = LoggerFactory.getLogger(PluginHeartbeatScheduler.class);
    private static final long MIN_INTERVAL_SECONDS = 30L;

    private final Project project;
    private final HttpClient httpClient;
    private final ScheduledFuture<?> future;

    private volatile long lastAttemptEpochSeconds = 0L;

    public PluginHeartbeatScheduler(@NotNull Project project) {
        this.project = project;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.future = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::tick, 10, 20, TimeUnit.SECONDS);
    }

    private void tick() {
        if (project.isDisposed()) {
            return;
        }
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        if (settings == null) {
            return;
        }
        settings.ensureInstanceKey();

        long interval = Math.max(MIN_INTERVAL_SECONDS, settings.heartbeatIntervalSeconds);
        long now = currentEpochSeconds();
        if (now - lastAttemptEpochSeconds < interval) {
            return;
        }
        lastAttemptEpochSeconds = now;

        String endpoint = normalizeBaseUrl(settings.baseUrl);
        if (endpoint.isBlank()) {
            return;
        }

        YgflowConfigurationResolver.ResolvedValue resolved = YgflowConfigurationResolver
                .resolveProjectKey(project, settings, false);
        String projectKey = resolved.value();
        if (projectKey.isBlank()) {
            return;
        }

        try {
            sendHeartbeat(endpoint, projectKey, settings.instanceKey);
        } catch (Exception ex) {
            LOG.debug("Heartbeat failed for project {}: {}", project.getName(), ex.getMessage());
            LOG.trace("Heartbeat failure stack", ex);
        }
    }

    public void triggerImmediate() {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            lastAttemptEpochSeconds = 0L;
            tick();
        });
    }

    private void sendHeartbeat(String endpoint, String projectKey, String instanceKey) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("instanceKey", instanceKey);
        payload.put("ideType", "IntelliJ");
        ApplicationInfo info = ApplicationInfo.getInstance();
        payload.put("ideVersion", info != null ? info.getFullVersion() : "Unknown");
        payload.put("status", "ONLINE");
        payload.put("extraInfo", buildExtraInfo(projectKey, instanceKey));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/projects/" + encode(projectKey) + "/plugins/heartbeat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status);
        }
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String encode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    private static String buildExtraInfo(String projectKey, String instanceKey) {
        JSONObject json = new JSONObject();
        json.put("source", "yglue-plugin");
        json.put("projectKey", projectKey);
        json.put("instanceKey", instanceKey);
        ApplicationInfo info = ApplicationInfo.getInstance();
        if (info != null) {
            json.put("ideVersion", info.getFullVersion());
        }
        return json.toString();
    }

    private static long currentEpochSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    @Override
    public void dispose() {
        if (future != null) {
            future.cancel(true);
        }
    }
}

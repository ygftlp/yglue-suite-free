package org.yglue.flow.idea.sync;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yglue.flow.idea.settings.YgflowSettingsState;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles orchestration rule synchronisation between the IntelliJ plugin and the YGlue orchestrator.
 */
public class RuleSyncService {

    private final YgflowSettingsState settings;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String projectKey;
    private final String instanceKey;
    private final Path rulesDirectory;

    public RuleSyncService(@NotNull YgflowSettingsState settings,
                           @NotNull String resolvedEndpoint,
                           @NotNull String resolvedProjectKey,
                           @NotNull Path rulesDirectory) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.endpoint = trimTrailingSlash(resolvedEndpoint);
        this.projectKey = resolvedProjectKey;
        this.instanceKey = Objects.requireNonNullElse(settings.instanceKey, "");
        this.rulesDirectory = rulesDirectory;
    }

    public SyncResult sync(@NotNull ProgressIndicator indicator) throws Exception {
        indicator.setIndeterminate(true);
        JSONObject heartbeat = sendHeartbeat();

        boolean needSync = heartbeat.optBoolean("needSync", false);
        JSONArray pending = heartbeat.optJSONArray("pendingFlows");
        if (!needSync || pending == null || pending.length() == 0) {
            downloadEntryPoints();
            return SyncResult.upToDate();
        }

        Files.createDirectories(rulesDirectory);

        List<SyncResult.Entry> entries = new ArrayList<>();
        indicator.setIndeterminate(false);

        for (int i = 0; i < pending.length(); i++) {
            indicator.checkCanceled();
            JSONObject flow = pending.getJSONObject(i);
            String flowCode = flow.optString("flowCode");
            int versionNo = flow.optInt("latestVersion", -1);
            indicator.setText("Downloading " + flowCode + " v" + versionNo);

            if (flowCode.isBlank() || versionNo < 0) {
                continue;
            }

            String json = fetchFlowContent(flowCode, versionNo);
            Path file = writeRuleFile(flowCode, json);
            indicator.setText2(file.toString());
            sendAck(flowCode, versionNo);
            entries.add(new SyncResult.Entry(flowCode, versionNo, file));
            indicator.setFraction((double) (i + 1) / pending.length());
        }

        indicator.setFraction(1.0);
        downloadEntryPoints();
        return SyncResult.synced(entries);
    }

    private JSONObject sendHeartbeat() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("instanceKey", instanceKey);
        payload.put("ideType", "IntelliJ");
        ApplicationInfo appInfo = ApplicationInfo.getInstance();
        payload.put("ideVersion", appInfo.getFullVersion());
        payload.put("status", "ONLINE");
        payload.put("extraInfo", createExtraInfo());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/projects/" + encode(projectKey) + "/plugins/heartbeat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "Heartbeat failed");
        return new JSONObject(response.body());
    }

    private String fetchFlowContent(String flowCode, int versionNo) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/projects/" + encode(projectKey)
                        + "/flows/" + encode(flowCode) + "/versions/" + versionNo))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "Fetch flow " + flowCode + " v" + versionNo + " failed");

        JSONObject body = new JSONObject(response.body());
        return body.optString("contentJson", "{}");
    }

    private void sendAck(String flowCode, int versionNo) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("flowCode", flowCode);
        payload.put("versionNo", versionNo);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/projects/" + encode(projectKey)
                        + "/plugins/" + encode(instanceKey) + "/sync/ack"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "ACK failed for " + flowCode);
    }

    private Path writeRuleFile(String flowCode, String contentJson) throws Exception {
        String fileName = sanitizeFileName(flowCode) + ".json";
        Path file = rulesDirectory.resolve(fileName);
        Files.writeString(file, contentJson, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    private void downloadEntryPoints() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/projects/" + encode(projectKey) + "/entrypoints"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return;
        }
        ensureSuccess(response, "Download entrypoints failed");
        Path baseDir = rulesDirectory.getParent();
        if (baseDir == null) {
            baseDir = rulesDirectory;
        }
        Files.createDirectories(baseDir);
        Path file = baseDir.resolve("entrypoints.json");
        Files.writeString(file, response.body(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void ensureSuccess(HttpResponse<?> response, String message) throws Exception {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new Exception(message + ": HTTP " + status);
        }
    }

    private static String trimTrailingSlash(String value) {
        String v = value;
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8);
    }

    private static String sanitizeFileName(String input) {
        if (input == null || input.isBlank()) {
            return "flow";
        }
        String sanitized = input.replaceAll("[\\\\/:*?\"<>|]", "_");
        return sanitized.isBlank() ? "flow" : sanitized;
    }

    @NlsSafe
    private String createExtraInfo() {
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

    public static class SyncResult {
        public final boolean upToDate;
        public final List<Entry> entries;

        private SyncResult(boolean upToDate, List<Entry> entries) {
            this.upToDate = upToDate;
            this.entries = entries;
        }

        public static SyncResult upToDate() {
            return new SyncResult(true, List.of());
        }

        public static SyncResult synced(List<Entry> entries) {
            return new SyncResult(false, entries);
        }

        public record Entry(String flowCode, int version, Path file) {}
    }
}

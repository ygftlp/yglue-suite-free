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
        
        Files.createDirectories(rulesDirectory);
        
        List<SyncResult.Entry> entries = new ArrayList<>();
        
        // 处理待同步的流程
        if (needSync && pending != null && pending.length() > 0) {
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
        }
        
        // 检查本地文件是否存在，如果缺失则重新下载
        indicator.setIndeterminate(true);
        indicator.setText("Checking local rule files...");
        List<SyncResult.Entry> missingFiles = checkAndDownloadMissingFiles(indicator);
        entries.addAll(missingFiles);
        
        indicator.setFraction(1.0);
        downloadEntryPoints();
        
        if (entries.isEmpty()) {
            return SyncResult.upToDate();
        } else {
            return SyncResult.synced(entries);
        }
    }
    
    /**
     * 检查本地规则文件是否存在，如果缺失则重新下载
     */
    private List<SyncResult.Entry> checkAndDownloadMissingFiles(@NotNull ProgressIndicator indicator) throws Exception {
        List<SyncResult.Entry> entries = new ArrayList<>();
        
        // 获取所有流程列表
        JSONArray allFlows = fetchAllFlows();
        if (allFlows == null || allFlows.length() == 0) {
            return entries;
        }
        
        indicator.setIndeterminate(false);
        indicator.setText("Checking missing local files...");
        
        for (int i = 0; i < allFlows.length(); i++) {
            indicator.checkCanceled();
            JSONObject flowObj = allFlows.getJSONObject(i);
            String flowCode = flowObj.optString("code");
            if (flowCode.isBlank()) {
                continue;
            }
            
            // 检查本地文件是否存在
            String fileName = sanitizeFileName(flowCode) + ".json";
            Path localFile = rulesDirectory.resolve(fileName);
            if (Files.exists(localFile)) {
                continue; // 文件存在，跳过
            }
            
            // 文件不存在，获取已发布的版本并下载
            try {
                JSONArray versions = fetchFlowVersions(flowCode);
                if (versions == null || versions.length() == 0) {
                    continue;
                }
                
                // 查找已发布的版本
                Integer publishedVersionNo = null;
                for (int j = 0; j < versions.length(); j++) {
                    JSONObject version = versions.getJSONObject(j);
                    if (version.optBoolean("published", false)) {
                        publishedVersionNo = version.optInt("versionNo", -1);
                        break;
                    }
                }
                
                // 如果没有已发布的版本，使用最新版本
                if (publishedVersionNo == null || publishedVersionNo < 0) {
                    JSONObject latestVersion = versions.getJSONObject(0);
                    publishedVersionNo = latestVersion.optInt("versionNo", -1);
                }
                
                if (publishedVersionNo != null && publishedVersionNo > 0) {
                    indicator.setText("Downloading missing file: " + flowCode + " v" + publishedVersionNo);
                    String json = fetchFlowContent(flowCode, publishedVersionNo);
                    Path file = writeRuleFile(flowCode, json);
                    indicator.setText2(file.toString());
                    sendAck(flowCode, publishedVersionNo);
                    entries.add(new SyncResult.Entry(flowCode, publishedVersionNo, file));
                }
            } catch (Exception ex) {
                // 如果获取版本失败，记录日志但继续处理其他流程
                // 这里可以添加日志记录
                continue;
            }
            
            indicator.setFraction((double) (i + 1) / allFlows.length());
        }
        
        return entries;
    }
    
    /**
     * 获取所有流程列表
     */
    private JSONArray fetchAllFlows() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/projects/" + encode(projectKey) + "/flows"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return new JSONArray();
        }
        ensureSuccess(response, "Fetch all flows failed");
        return new JSONArray(response.body());
    }
    
    /**
     * 获取指定流程的所有版本
     */
    private JSONArray fetchFlowVersions(String flowCode) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/projects/" + encode(projectKey)
                        + "/flows/" + encode(flowCode) + "/versions"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return new JSONArray();
        }
        ensureSuccess(response, "Fetch flow versions failed for " + flowCode);
        return new JSONArray(response.body());
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

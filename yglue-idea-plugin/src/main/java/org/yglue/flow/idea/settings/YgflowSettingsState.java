package org.yglue.flow.idea.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Service(Service.Level.APP)
@State(name = "YgflowSettings", storages = @Storage("ygflow_settings.xml"))
public final class YgflowSettingsState implements PersistentStateComponent<YgflowSettingsState> {

    public String baseUrl = "http://localhost:8090";
    public String projectKey = "";
    public String instanceKey = UUID.randomUUID().toString();
    public String rulesDir = ".ygflow/rules";
    public int statusCheckIntervalSeconds = 5;
    public boolean autoUploadEnabled = true;
    public int autoUploadIntervalSeconds = 60;
    public int heartbeatIntervalSeconds = 60;
    public boolean autoRuleSyncEnabled = true;
    public int autoRuleSyncIntervalSeconds = 60;

    public static YgflowSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(YgflowSettingsState.class);
    }

    public boolean isConfigured() {
        return isNotBlank(baseUrl) && isNotBlank(projectKey) && isNotBlank(instanceKey) && isNotBlank(rulesDir);
    }

    private boolean isNotBlank(String v) {
        return v != null && !v.isBlank();
    }

    @Override
    public @Nullable YgflowSettingsState getState() {
        ensureInstanceKey();
        return this;
    }

    @Override
    public void loadState(@NotNull YgflowSettingsState state) {
        this.baseUrl = state.baseUrl;
        this.projectKey = state.projectKey;
        this.instanceKey = state.instanceKey;
        this.rulesDir = state.rulesDir;
        this.statusCheckIntervalSeconds = state.statusCheckIntervalSeconds > 0 ? state.statusCheckIntervalSeconds : 5;
        this.autoUploadEnabled = state.autoUploadEnabled;
        this.autoUploadIntervalSeconds = state.autoUploadIntervalSeconds > 0 ? state.autoUploadIntervalSeconds : 60;
        this.heartbeatIntervalSeconds = state.heartbeatIntervalSeconds > 0 ? state.heartbeatIntervalSeconds : 60;
        this.autoRuleSyncEnabled = state.autoRuleSyncEnabled;
        this.autoRuleSyncIntervalSeconds = state.autoRuleSyncIntervalSeconds > 0 ? state.autoRuleSyncIntervalSeconds : 60;
        ensureInstanceKey();
    }

    public void ensureInstanceKey() {
        if (instanceKey == null || instanceKey.isBlank()) {
            instanceKey = UUID.randomUUID().toString();
        }
    }
}

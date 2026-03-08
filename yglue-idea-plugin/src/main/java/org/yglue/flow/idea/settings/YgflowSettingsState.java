package org.yglue.flow.idea.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * YGlue 插件设置状态
 * <p>
 * 存储插件的持久化配置，包括服务器地址、项目标识、实例标识、规则目录等。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
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
    public boolean jarUploadEnabled = false;
    public Set<String> selectedJarCoordinates = new HashSet<>();
    public boolean codeSnapshotAutoUploadEnabled = true;
    public int codeSnapshotUploadIntervalSeconds = 30;

    public String scanIncludes = "";
    public String scanExcludes = "";

    /**
     * 获取设置实例
     *
     * @return 设置状态实例
     */
    public static YgflowSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(YgflowSettingsState.class);
    }

    /**
     * 检查配置是否完整
     *
     * @return 如果所有必需字段都已配置则返回 true
     */
    public boolean isConfigured() {
        return isNotBlank(baseUrl) && isNotBlank(projectKey) && isNotBlank(instanceKey) && isNotBlank(rulesDir);
    }

    /**
     * 检查字符串是否非空
     *
     * @param v 字符串值
     * @return 如果非空则返回 true
     */
    private boolean isNotBlank(String v) {
        return v != null && !v.isBlank();
    }

    /**
     * 获取状态
     * <p>
     * 在保存状态前确保实例标识已设置。
     * </p>
     *
     * @return 状态对象
     */
    @Override
    public @Nullable YgflowSettingsState getState() {
        ensureInstanceKey();
        return this;
    }

    /**
     * 加载状态
     * <p>
     * 从持久化存储中加载状态，并确保所有字段都有合理的默认值。
     * </p>
     *
     * @param state 状态对象
     */
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
        this.autoRuleSyncIntervalSeconds = state.autoRuleSyncIntervalSeconds > 0 ? state.autoRuleSyncIntervalSeconds
                : 60;
        this.jarUploadEnabled = state.jarUploadEnabled;
        this.selectedJarCoordinates = state.selectedJarCoordinates == null
                ? new HashSet<>()
                : new HashSet<>(state.selectedJarCoordinates);
        this.codeSnapshotAutoUploadEnabled = state.codeSnapshotAutoUploadEnabled;
        this.codeSnapshotUploadIntervalSeconds = state.codeSnapshotUploadIntervalSeconds > 0
                ? state.codeSnapshotUploadIntervalSeconds
                : 30;
        this.scanIncludes = state.scanIncludes == null ? "" : state.scanIncludes;
        this.scanExcludes = state.scanExcludes == null ? "" : state.scanExcludes;
        ensureInstanceKey();
    }

    /**
     * 确保实例标识已设置
     * <p>
     * 如果实例标识为空，则生成一个新的 UUID。
     * </p>
     */
    public void ensureInstanceKey() {
        if (instanceKey == null || instanceKey.isBlank()) {
            instanceKey = UUID.randomUUID().toString();
        }
    }
}

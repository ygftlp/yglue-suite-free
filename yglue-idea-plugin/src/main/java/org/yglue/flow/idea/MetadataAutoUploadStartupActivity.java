package org.yglue.flow.idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * 项目启动活动
 * <p>
 * 在项目启动时初始化以下服务：
 * - 元数据自动上传调度器
 * - 插件心跳调度器
 * - 规则自动同步调度器
 * - 文件变化监听器
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class MetadataAutoUploadStartupActivity implements StartupActivity.DumbAware {

    /**
     * 运行启动活动
     *
     * @param project 项目对象
     */
    @Override
    public void runActivity(@NotNull Project project) {
        // 初始化元数据自动上传调度器
        MetadataAutoUploadScheduler scheduler = project.getService(MetadataAutoUploadScheduler.class);
        if (scheduler != null) {
            scheduler.onSettingsChanged();
        }

        // 初始化代码快照自动上传调度器
        CodeSnapshotAutoUploadScheduler codeScheduler = project.getService(CodeSnapshotAutoUploadScheduler.class);
        if (codeScheduler != null) {
            codeScheduler.onSettingsChanged();
        }
        
        // 初始化心跳调度器
        PluginHeartbeatScheduler heartbeatScheduler = project.getService(PluginHeartbeatScheduler.class);
        if (heartbeatScheduler != null) {
            heartbeatScheduler.triggerImmediate();
        }
        
        // 初始化规则同步调度器
        RuleAutoSyncScheduler ruleScheduler = project.getService(RuleAutoSyncScheduler.class);
        if (ruleScheduler != null) {
            ruleScheduler.onSettingsChanged();
        }
        
        // 初始化文件变化监听器（用于实时上报）
        MetadataChangeListener changeListener = project.getService(MetadataChangeListener.class);
        if (changeListener != null) {
            // 监听器已在构造函数中注册，这里只是确保服务被创建
        }
    }
}

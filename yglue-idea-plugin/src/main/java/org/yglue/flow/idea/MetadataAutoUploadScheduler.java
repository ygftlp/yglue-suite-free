package org.yglue.flow.idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.idea.settings.YgflowSettingsState;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 元数据自动上传调度器
 * <p>
 * 定时检查并上传项目元数据到 YGlue 编排器服务器。
 * 支持配置上传间隔，并提供立即触发上传的功能。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
@Service(Service.Level.PROJECT)
public final class MetadataAutoUploadScheduler implements Disposable {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataAutoUploadScheduler.class);

    private final Project project;
    private final ScheduledFuture<?> future;
    private volatile long lastUploadEpochSeconds = 0L;

    /**
     * 构造函数
     * <p>
     * 启动定时检查任务，初始延迟10秒，之后每10秒检查一次。
     * </p>
     *
     * @param project 项目对象
     */
    public MetadataAutoUploadScheduler(@NotNull Project project) {
        this.project = project;
        this.future = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::tick, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * 定时检查任务
     * <p>
     * 每10秒执行一次，检查是否需要上传元数据。
     * </p>
     */
    private void tick() {
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        if (project.isDisposed() || settings == null || !settings.autoUploadEnabled) {
            return;
        }
        long intervalSeconds = Math.max(5L, (long) settings.autoUploadIntervalSeconds);
        long now = System.currentTimeMillis() / 1000;
        if (lastUploadEpochSeconds != 0 && (now - lastUploadEpochSeconds) < intervalSeconds) {
            return;
        }
        boolean success = UploadMetadataAction.upload(project, false);
        if (success) {
            lastUploadEpochSeconds = now;
        } else {
            LOG.debug("Auto upload skipped or failed for project {}", project.getName());
        }
    }

    /**
     * 立即触发上传
     * <p>
     * 在后台线程中立即执行一次上传操作，不等待定时任务。
     * </p>
     */
    public void triggerImmediate() {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            YgflowSettingsState settings = YgflowSettingsState.getInstance();
            if (project.isDisposed() || settings == null || !settings.autoUploadEnabled) {
                return;
            }
            boolean success = UploadMetadataAction.upload(project, false);
            if (success) {
                lastUploadEpochSeconds = System.currentTimeMillis() / 1000;
            } else {
                LOG.debug("Immediate upload failed for project {}", project.getName());
            }
        });
    }

    /**
     * 设置变更回调
     * <p>
     * 当设置变更时，重置上次上传时间并触发立即上传。
     * </p>
     */
    public void onSettingsChanged() {
        lastUploadEpochSeconds = 0L;
        triggerImmediate();
    }

    @Override
    public void dispose() {
        if (future != null) {
            future.cancel(true);
        }
    }
}

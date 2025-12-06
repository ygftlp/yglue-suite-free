package org.yglue.flow.idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.idea.settings.YgflowSettingsState;
import org.yglue.flow.idea.snapshot.UploadCodeSnapshotAction;

@Service(Service.Level.PROJECT)
public final class CodeSnapshotAutoUploadScheduler implements Disposable {

    private static final Logger LOG = LoggerFactory.getLogger(CodeSnapshotAutoUploadScheduler.class);

    private final Project project;
    private final ScheduledFuture<?> future;
    private volatile long lastUploadEpochSeconds = 0L;

    public CodeSnapshotAutoUploadScheduler(@NotNull Project project) {
        this.project = project;
        this.future = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::tick, 5, 5, TimeUnit.SECONDS);
    }

    private void tick() {
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        if (project.isDisposed() || settings == null || !settings.codeSnapshotAutoUploadEnabled) {
            return;
        }
        long intervalSeconds = Math.max(5L, settings.codeSnapshotUploadIntervalSeconds);
        long now = System.currentTimeMillis() / 1000;
        if (lastUploadEpochSeconds != 0 && (now - lastUploadEpochSeconds) < intervalSeconds) {
            return;
        }
        boolean success = UploadCodeSnapshotAction.upload(project, false);
        if (success) {
            lastUploadEpochSeconds = now;
        } else {
            LOG.debug("Auto code code upload skipped for project {}", project.getName());
        }
    }

    public void triggerImmediate() {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            YgflowSettingsState settings = YgflowSettingsState.getInstance();
            if (project.isDisposed() || settings == null || !settings.codeSnapshotAutoUploadEnabled) {
                return;
            }
            boolean success = UploadCodeSnapshotAction.upload(project, false);
            if (success) {
                lastUploadEpochSeconds = System.currentTimeMillis() / 1000;
            }
        });
    }

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

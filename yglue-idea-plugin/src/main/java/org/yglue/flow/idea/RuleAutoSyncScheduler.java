package org.yglue.flow.idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.idea.settings.YgflowSettingsState;
import org.yglue.flow.idea.sync.RuleSyncService;
import org.yglue.flow.idea.util.YgflowConfigurationResolver;

import java.nio.file.Path;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service(Service.Level.PROJECT)
public final class RuleAutoSyncScheduler implements Disposable {

    private static final Logger LOG = LoggerFactory.getLogger(RuleAutoSyncScheduler.class);
    /**
     * 最小同步间隔（秒）
     * 开发模式下使用更短的间隔以提供更好的实时体验
     */
    private static final long MIN_INTERVAL_SECONDS = 10L;
    /**
     * 默认检查间隔（秒）
     * 从30秒缩短到10秒，提高响应速度
     */
    private static final long DEFAULT_CHECK_INTERVAL_SECONDS = 10L;
    private static final String DEFAULT_BASE_URL = "http://localhost:8090";

    private final Project project;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledFuture<?> future;
    private volatile long lastAttemptEpochSeconds = 0L;

    /**
     * 构造函数
     * 启动定时检查任务，初始延迟20秒，之后每10秒检查一次
     */
    public RuleAutoSyncScheduler(@NotNull Project project) {
        this.project = project;
        this.future = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::tick, 20, DEFAULT_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 定时检查任务
     * 每10秒执行一次，检查是否有待同步的规则文件
     */
    private void tick() {
        if (project.isDisposed()) {
            return;
        }
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        if (settings == null || !settings.autoRuleSyncEnabled) {
            return;
        }

        // 计算同步间隔：如果配置的间隔小于最小间隔，使用最小间隔（开发模式）
        // 如果配置的间隔大于最小间隔，使用配置的间隔（生产模式）
        long configuredIntervalSeconds = (long) settings.autoRuleSyncIntervalSeconds;
        long intervalSeconds = Math.max(MIN_INTERVAL_SECONDS, configuredIntervalSeconds);
        
        long now = currentEpochSeconds();
        // 如果距离上次同步时间小于间隔时间，跳过本次检查
        if (lastAttemptEpochSeconds != 0 && (now - lastAttemptEpochSeconds) < intervalSeconds) {
            return;
        }
        
        // 防止并发执行
        if (!running.compareAndSet(false, true)) {
            return;
        }
        
        try {
            performSync(settings);
        } finally {
            lastAttemptEpochSeconds = currentEpochSeconds();
            running.set(false);
        }
    }

    private void performSync(YgflowSettingsState settings) {
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            return;
        }

        String endpoint = normalizeBaseUrl(settings.baseUrl);
        if (endpoint.isBlank()) {
            return;
        }

        YgflowConfigurationResolver.ResolvedValue resolvedProject =
                YgflowConfigurationResolver.resolveProjectKey(project, settings, false);
        String projectKey = resolvedProject.value();
        if (projectKey.isBlank()) {
            return;
        }

        Path rulesDir;
        try {
            rulesDir = resolveRulesDirectory(basePath, settings.rulesDir);
        } catch (Exception ex) {
            LOG.warn("Failed to resolve rules directory for project {}: {}", project.getName(), ex.getMessage());
            LOG.debug("Rules directory resolution failure", ex);
            return;
        }

        settings.ensureInstanceKey();
        RuleSyncService service = new RuleSyncService(settings, endpoint, projectKey, rulesDir);
        EmptyProgressIndicator indicator = new EmptyProgressIndicator();
        indicator.setIndeterminate(true);
        try {
            RuleSyncService.SyncResult result = service.sync(indicator);
            if (!result.upToDate) {
                LOG.info("yglue auto rule sync updated {} flow(s) for project {}", result.entries.size(), project.getName());
            } else {
                LOG.debug("yglue auto rule sync found no updates for project {}", project.getName());
            }
        } catch (Exception ex) {
            LOG.warn("yglue auto rule sync failed for project {}: {}", project.getName(), ex.getMessage());
            LOG.debug("Auto rule sync failure stack", ex);
        }
    }

    public void onSettingsChanged() {
        lastAttemptEpochSeconds = 0L;
        if (autoSyncEnabled()) {
            triggerImmediate();
        }
    }

    public void triggerImmediate() {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            if (project.isDisposed()) {
                return;
            }
            YgflowSettingsState settings = YgflowSettingsState.getInstance();
            if (settings == null || !settings.autoRuleSyncEnabled) {
                return;
            }
            if (!running.compareAndSet(false, true)) {
                return;
            }
            try {
                performSync(settings);
            } finally {
                lastAttemptEpochSeconds = currentEpochSeconds();
                running.set(false);
            }
        });
    }

    private boolean autoSyncEnabled() {
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        return settings != null && settings.autoRuleSyncEnabled;
    }

    private static String normalizeBaseUrl(String configured) {
        String value = configured == null ? "" : configured.trim();
        if (value.isEmpty()) {
            value = DEFAULT_BASE_URL;
        }
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static Path resolveRulesDirectory(String basePath, String rulesDirRaw) {
        String value = rulesDirRaw == null || rulesDirRaw.isBlank()
                ? ".ygflow/rules"
                : rulesDirRaw.trim();
        Path path = Path.of(value);
        if (!path.isAbsolute()) {
            path = Path.of(basePath).resolve(path);
        }
        return path.normalize();
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

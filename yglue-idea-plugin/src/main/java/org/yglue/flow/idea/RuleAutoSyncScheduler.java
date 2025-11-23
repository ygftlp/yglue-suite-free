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

/**
 * 规则自动同步调度器
 * <p>
 * 定时检查并同步流程规则文件到本地项目。
 * 支持配置同步间隔，并提供立即触发同步的功能。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
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

    /**
     * 执行同步操作
     *
     * @param settings 插件设置状态
     */
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

    /**
     * 设置变更回调
     * <p>
     * 当设置变更时，重置上次同步时间并触发立即同步。
     * </p>
     */
    public void onSettingsChanged() {
        lastAttemptEpochSeconds = 0L;
        if (autoSyncEnabled()) {
            triggerImmediate();
        }
    }

    /**
     * 立即触发同步
     * <p>
     * 在后台线程中立即执行一次同步操作，不等待定时任务。
     * </p>
     */
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

    /**
     * 检查自动同步是否启用
     *
     * @return 如果启用则返回 true
     */
    private boolean autoSyncEnabled() {
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        return settings != null && settings.autoRuleSyncEnabled;
    }

    /**
     * 规范化基础 URL
     * <p>
     * 移除末尾的斜杠，如果为空则使用默认值。
     * </p>
     *
     * @param configured 配置的 URL
     * @return 规范化后的 URL
     */
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

    /**
     * 解析规则目录路径
     *
     * @param basePath 项目根路径
     * @param rulesDirRaw 规则目录路径（相对或绝对）
     * @return 规范化后的规则目录路径
     */
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

    /**
     * 获取当前时间戳（秒）
     *
     * @return 当前时间戳（秒）
     */
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

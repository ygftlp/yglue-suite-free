package org.yglue.flow.idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yglue.flow.idea.settings.YgflowSettingsState;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 元数据变化监听器
 * 监听项目文件变化，当检测到相关的 Java 文件变化时，实时触发元数据上报
 */
@Service(Service.Level.PROJECT)
public final class MetadataChangeListener implements BulkFileListener, Disposable {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataChangeListener.class);

    private final Project project;
    private final AtomicLong lastTriggerTime = new AtomicLong(0);
    private static final long DEBOUNCE_MS = 2000; // 防抖：2秒内的多次变化只触发一次

    public MetadataChangeListener(@NotNull Project project) {
        this.project = project;
        // 注册文件变化监听器
        project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
        LOG.debug("Metadata change listener registered for project {}", project.getName());
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        // 检查是否启用了自动上传
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        if (settings == null || !settings.autoUploadEnabled) {
            return;
        }

        // 检查是否有相关的 Java 文件变化
        boolean hasRelevantChange = false;
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file != null && isRelevantFile(file)) {
                hasRelevantChange = true;
                break;
            }
        }

        if (!hasRelevantChange) {
            return;
        }

        // 防抖处理：避免短时间内多次触发
        long now = System.currentTimeMillis();
        long lastTime = lastTriggerTime.get();
        if (lastTime > 0 && (now - lastTime) < DEBOUNCE_MS) {
            LOG.debug("Metadata change detected but debounced for project {}", project.getName());
            return;
        }

        if (lastTriggerTime.compareAndSet(lastTime, now)) {
            LOG.info("Relevant file change detected, triggering metadata upload for project {}", project.getName());
            // 触发立即上报
            MetadataAutoUploadScheduler scheduler = project.getService(MetadataAutoUploadScheduler.class);
            if (scheduler != null) {
                scheduler.triggerImmediate();
            }
        }
    }

    /**
     * 判断文件是否与元数据相关
     * 检查是否为 Java 文件，并且可能包含 FlowApi 或 RestController 注解
     */
    private boolean isRelevantFile(@NotNull VirtualFile file) {
        // 只处理 Java 文件
        if (!file.getName().endsWith(".java")) {
            return false;
        }

        // 检查文件是否在源代码目录中（排除测试代码和生成的代码）
        String path = file.getPath();
        if (path.contains("/test/") || path.contains("/generated/") || path.contains("/target/")) {
            return false;
        }

        // 尝试解析为 PSI 文件，检查是否包含相关注解
        try {
            PsiManager psiManager = PsiManager.getInstance(project);
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile instanceof PsiJavaFile) {
                // 这里可以进一步检查是否包含 @FlowApi 或 @RestController 等注解
                // 为了性能考虑，暂时只检查文件扩展名和路径
                return true;
            }
        } catch (Exception e) {
            LOG.debug("Failed to check file relevance: {}", e.getMessage());
        }

        return false;
    }

    @Override
    public void dispose() {
        LOG.debug("Metadata change listener disposed for project {}", project.getName());
    }
}


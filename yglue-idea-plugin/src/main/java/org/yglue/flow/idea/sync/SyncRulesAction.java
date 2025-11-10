package org.yglue.flow.idea.sync;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.yglue.flow.idea.settings.YgflowSettingsConfigurable;
import org.yglue.flow.idea.settings.YgflowSettingsState;
import org.yglue.flow.idea.util.YgflowConfigurationResolver;

import java.nio.file.Path;
import java.util.Objects;

public class SyncRulesAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            Messages.showErrorDialog(project, "Project base path is unavailable.", "yglue Sync");
            return;
        }

        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        if (settings == null) {
            Messages.showErrorDialog(project, "yglue settings service is unavailable.", "yglue Sync");
            return;
        }
        settings.ensureInstanceKey();

        String endpoint = defaultBaseUrl(settings.baseUrl);
        YgflowConfigurationResolver.ResolvedValue resolvedProject =
                YgflowConfigurationResolver.resolveProjectKey(project, settings);
        String projectKey = resolvedProject.value();

        if (endpoint.isBlank() || projectKey.isBlank()) {
            Messages.showInfoMessage(project,
                    "Please configure yglue endpoint and project key before syncing rules.",
                    "yglue Sync");
            ShowSettingsUtil.getInstance().showSettingsDialog(project, YgflowSettingsConfigurable.class);
            return;
        }

        String rulesDirRaw = Objects.requireNonNullElse(settings.rulesDir, ".ygflow/rules").trim();
        if (rulesDirRaw.isEmpty()) {
            rulesDirRaw = ".ygflow/rules";
        }
        Path rulesDir = resolveRulesDirectory(basePath, rulesDirRaw);

        RuleSyncService service = new RuleSyncService(settings, endpoint, projectKey, rulesDir);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Sync yglue Rules", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    RuleSyncService.SyncResult result = service.sync(indicator);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (project.isDisposed()) {
                            return;
                        }
                        if (result.upToDate || result.entries.isEmpty()) {
                            Messages.showInfoMessage(project, "All rules are already up to date.", "yglue Sync");
                        } else {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Synced ").append(result.entries.size()).append(" flow(s):\n");
                            result.entries.forEach(entry ->
                                    sb.append("- ").append(entry.flowCode())
                                            .append(" v").append(entry.version())
                                            .append(" -> ").append(entry.file())
                                            .append("\n"));
                            Messages.showInfoMessage(project, sb.toString(), "yglue Sync");
                        }
                    }, ModalityState.NON_MODAL);
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (!project.isDisposed()) {
                            Messages.showErrorDialog(project,
                                    "Rule sync failed: " + ex.getMessage(),
                                    "yglue Sync");
                        }
                    }, ModalityState.NON_MODAL);
                }
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    private static String defaultBaseUrl(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "http://localhost:8090";
    }

    private static Path resolveRulesDirectory(String basePath, String rulesDir) {
        Path path = Path.of(rulesDir);
        if (!path.isAbsolute()) {
            path = Path.of(basePath).resolve(path);
        }
        return path.normalize();
    }
}

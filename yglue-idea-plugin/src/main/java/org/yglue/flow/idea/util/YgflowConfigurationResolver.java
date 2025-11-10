package org.yglue.flow.idea.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yglue.flow.idea.settings.YgflowSettingsState;

import java.nio.file.Path;

/**
 * Resolves configuration values (project key, etc.) using environment, persisted state, project context or user input.
 */
public final class YgflowConfigurationResolver {

    private YgflowConfigurationResolver() {
    }

    public static ResolvedValue resolveProjectKey(@Nullable Project project,
                                                  @NotNull YgflowSettingsState settings) {
        return resolveProjectKey(project, settings, true);
    }

    public static ResolvedValue resolveProjectKey(@Nullable Project project,
                                                  @NotNull YgflowSettingsState settings,
                                                  boolean promptIfMissing) {
        String fromOverrides = firstNonBlank(
                System.getProperty("yglue.project"),
                System.getProperty("ygflow.project"),
                System.getenv("YGLUE_PROJECT"),
                System.getenv("YGFLOW_PROJECT"),
                trim(settings.projectKey)
        );
        if (!fromOverrides.isBlank()) {
            return new ResolvedValue(fromOverrides, ResolvedValue.Source.CONFIGURED);
        }

        String inferred = inferFromProject(project);
        if (!inferred.isBlank()) {
            if (!inferred.equals(settings.projectKey)) {
                settings.projectKey = inferred;
                requestSaveSettings();
            }
            return new ResolvedValue(inferred, ResolvedValue.Source.INFERRED);
        }

        if (promptIfMissing) {
            String selected = promptForProjectDirectory(project);
            if (!selected.isBlank()) {
                if (!selected.equals(settings.projectKey)) {
                    settings.projectKey = selected;
                    requestSaveSettings();
                }
                return new ResolvedValue(selected, ResolvedValue.Source.USER_SELECTED);
            }
        }

        return ResolvedValue.empty();
    }

    private static String inferFromProject(@Nullable Project project) {
        if (project == null) return "";
        String basePath = project.getBasePath();
        if (basePath != null && !basePath.isBlank()) {
            Path base = Path.of(basePath);
            Path fileName = base.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        }
        String projectName = project.getName();
        return projectName == null ? "" : projectName.trim();
    }

    private static String promptForProjectDirectory(@Nullable Project project) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setTitle("Select Project Directory");
        descriptor.setDescription("Choose the directory whose name should be used as the yglue project key.");

        VirtualFile toSelect = baseVirtualFile(project);
        VirtualFile chosen = FileChooser.chooseFile(descriptor, project, toSelect);
        if (chosen != null) {
            return chosen.getName();
        }

        String suggestion = inferFromProject(project);
        String input = Messages.showInputDialog(
                project,
                "Enter the yglue project key:",
                "yglue Project Key",
                null,
                suggestion.isBlank() ? "demo" : suggestion,
                null
        );
        return input == null ? "" : input.trim();
    }

    @Nullable
    private static VirtualFile baseVirtualFile(@Nullable Project project) {
        if (project == null) {
            return null;
        }
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            return null;
        }
        return LocalFileSystem.getInstance().findFileByPath(basePath);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requestSaveSettings() {
        ApplicationManager.getApplication().invokeLater(
                () -> ApplicationManager.getApplication().saveSettings()
        );
    }

    public record ResolvedValue(String value, Source source) {
        public enum Source {
            CONFIGURED,
            INFERRED,
            USER_SELECTED,
            NONE
        }

        public static ResolvedValue empty() {
            return new ResolvedValue("", Source.NONE);
        }
    }

    public static String inferProjectKey(@Nullable Project project) {
        return inferFromProject(project);
    }

    public static String inferProjectKey(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            Path p = Path.of(path);
            Path fileName = p.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}

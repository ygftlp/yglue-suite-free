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
 * YGlue 配置解析器
 * <p>
 * 解析配置值（如项目标识等），支持从以下来源获取：
 * - 系统属性（yglue.project、ygflow.project）
 * - 环境变量（YGLUE_PROJECT、YGFLOW_PROJECT）
 * - 持久化设置
 * - 项目上下文推断
 * - 用户输入
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public final class YgflowConfigurationResolver {

    /**
     * 私有构造函数，防止实例化
     */
    private YgflowConfigurationResolver() {
    }

    /**
     * 解析项目标识
     * <p>
     * 如果项目标识缺失，会提示用户输入。
     * </p>
     *
     * @param project 项目对象
     * @param settings 插件设置状态
     * @return 解析结果
     */
    public static ResolvedValue resolveProjectKey(@Nullable Project project,
                                                  @NotNull YgflowSettingsState settings) {
        return resolveProjectKey(project, settings, true);
    }

    /**
     * 解析项目标识（带提示选项）
     *
     * @param project 项目对象
     * @param settings 插件设置状态
     * @param promptIfMissing 如果缺失是否提示用户输入
     * @return 解析结果
     */
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

    /**
     * 从项目推断项目标识
     * <p>
     * 优先使用项目根目录名称，如果没有则使用项目名称。
     * </p>
     *
     * @param project 项目对象
     * @return 推断的项目标识
     */
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

    /**
     * 提示用户选择项目目录
     * <p>
     * 如果用户取消选择，则提示输入项目标识。
     * </p>
     *
     * @param project 项目对象
     * @return 用户选择或输入的项目标识
     */
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

    /**
     * 获取项目根目录的虚拟文件
     *
     * @param project 项目对象
     * @return 虚拟文件对象，如果不存在则返回 null
     */
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

    /**
     * 获取第一个非空字符串
     *
     * @param candidates 候选字符串数组
     * @return 第一个非空字符串，如果都为空则返回空字符串
     */
    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }

    /**
     * 修剪字符串
     *
     * @param value 字符串值
     * @return 修剪后的字符串，如果为 null 则返回空字符串
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 请求保存设置
     * <p>
     * 在 EDT（事件分发线程）中异步保存设置。
     * </p>
     */
    private static void requestSaveSettings() {
        ApplicationManager.getApplication().invokeLater(
                () -> ApplicationManager.getApplication().saveSettings()
        );
    }

    /**
     * 解析结果值记录
     *
     * @param value 解析的值
     * @param source 值来源
     */
    public record ResolvedValue(String value, Source source) {
        /**
         * 值来源枚举
         */
        public enum Source {
            /** 从配置中获取 */
            CONFIGURED,
            /** 从项目推断 */
            INFERRED,
            /** 用户选择 */
            USER_SELECTED,
            /** 无值 */
            NONE
        }

        /**
         * 创建空值结果
         *
         * @return 空值结果
         */
        public static ResolvedValue empty() {
            return new ResolvedValue("", Source.NONE);
        }
    }

    /**
     * 从项目推断项目标识
     *
     * @param project 项目对象
     * @return 推断的项目标识
     */
    public static String inferProjectKey(@Nullable Project project) {
        return inferFromProject(project);
    }

    /**
     * 从路径推断项目标识
     * <p>
     * 使用路径的最后一部分作为项目标识。
     * </p>
     *
     * @param path 路径字符串
     * @return 推断的项目标识
     */
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

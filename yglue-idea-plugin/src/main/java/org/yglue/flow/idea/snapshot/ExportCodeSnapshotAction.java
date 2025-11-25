package org.yglue.flow.idea.snapshot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.yglue.flow.idea.settings.YgflowSettingsState;

/**
 * 手动导出代码语义快照。
 */
public class ExportCodeSnapshotAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        YgflowSettingsState settings = YgflowSettingsState.getInstance();
        if (settings == null) {
            Messages.showErrorDialog(project, "无法读取 yglue 配置。", "yglue");
            return;
        }
        try {
            JSONObject snapshot = CodeSnapshotExporter.buildSnapshot(project, settings);
            Path outFile = CodeSnapshotExporter.writeSnapshot(project, snapshot);
            Messages.showInfoMessage(project, "代码语义快照已导出至 " + outFile, "yglue");
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "导出失败：" + ex.getMessage(), "yglue");
        }
    }
}

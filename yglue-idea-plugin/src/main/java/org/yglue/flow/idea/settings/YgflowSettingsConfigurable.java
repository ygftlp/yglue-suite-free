package org.yglue.flow.idea.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CheckBoxListListener;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.yglue.flow.idea.CodeSnapshotAutoUploadScheduler;
import org.yglue.flow.idea.MetadataAutoUploadScheduler;
import org.yglue.flow.idea.PluginHeartbeatScheduler;
import org.yglue.flow.idea.RuleAutoSyncScheduler;
import org.yglue.flow.idea.util.JarDependencyResolver;
import org.yglue.flow.idea.util.YgflowConfigurationResolver;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * YGlue 插件设置配置界面
 * <p>
 * 提供插件设置的图形化配置界面，包括：
 * - 服务器地址配置
 * - 项目标识配置
 * - 实例标识配置
 * - 规则目录配置
 * - 自动上传和同步设置
 * - 连接状态检查
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class YgflowSettingsConfigurable implements Configurable {

    private static final String DEFAULT_BASE_URL = "http://localhost:8090";
    private static final String DEFAULT_RULES_DIR = ".ygflow/rules";
    private static final String STATUS_NOT_CHECKED = "Status: Not checked";
    private static final String STATUS_CHECKING = "Status: Checking...";

    private JPanel panel;
    private JBTextField baseUrlField;
    private JBTextField projectKeyField;
    private JBLabel projectKeySourceLabel;
    private JBTextField instanceKeyField;
    private TextFieldWithBrowseButton rulesDirField;
    private JCheckBox autoUploadCheck;
    private JBTextField autoUploadIntervalField;
    private JCheckBox autoRuleSyncCheck;
    private JBTextField autoRuleSyncIntervalField;
    private JBTextField statusIntervalField;
    private JBLabel statusLabel;
    private ScheduledFuture<?> statusCheckFuture;
    private JCheckBox jarUploadCheck;
    private JCheckBox autoCodeSnapshotUploadCheck;
    private JBTextField autoCodeSnapshotUploadIntervalField;
    private CheckBoxList<String> jarSelectionList;
    private JButton jarRefreshButton;
    private JBLabel jarListHint;
    private final Set<String> jarSelectionState = new HashSet<>();
    private JBTextField scanIncludesField;
    private JBTextField scanExcludesField;

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimOrDefault(String value, String fallback) {
        String v = trim(value);
        return v.isEmpty() ? fallback : v;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private static String trimTrailingSlash(String value) {
        String v = trim(value);
        while (v.endsWith("/") && v.length() > 1) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 117) + "...";
    }

    private static String describeStatus(String body, String fallback) {
        JSONObject json = parseJson(body);
        if (json == null) {
            return fallback;
        }
        String status = json.optString("status");
        return status.isBlank() ? fallback : status;
    }

    private static JSONObject parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return new JSONObject(body);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "YGlue";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) {
            baseUrlField = new JBTextField();
            baseUrlField.getEmptyText().setText(DEFAULT_BASE_URL);
            baseUrlField.getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@NotNull DocumentEvent e) {
                    markStatusUnknown();
                }
            });

            projectKeyField = new JBTextField();
            projectKeyField.getEmptyText().setText("Defaults to current project name");
            projectKeyField.setEditable(false);
            projectKeyField.setBorder(JBUI.Borders.empty());
            projectKeyField.setOpaque(false);
            projectKeySourceLabel = new JBLabel();
            projectKeySourceLabel.setFont(JBFont.small());
            projectKeySourceLabel.setBorder(JBUI.Borders.emptyLeft(6));

            instanceKeyField = new JBTextField();
            JButton regenerateButton = new JButton("Regenerate");
            regenerateButton.addActionListener(e -> instanceKeyField.setText(UUID.randomUUID().toString()));

            JPanel instancePanel = new JPanel(new BorderLayout(8, 0));
            instancePanel.add(instanceKeyField, BorderLayout.CENTER);
            instancePanel.add(regenerateButton, BorderLayout.EAST);

            rulesDirField = new TextFieldWithBrowseButton();
            // 设置占位符文本
            javax.swing.JTextField textField = rulesDirField.getTextField();
            if (textField instanceof JBTextField) {
                ((JBTextField) textField).getEmptyText().setText(DEFAULT_RULES_DIR);
            } else {
                textField.setToolTipText("默认: " + DEFAULT_RULES_DIR);
            }

            // 配置文件夹选择器
            FileChooserDescriptor folderDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            folderDescriptor.setTitle("Select Rules Directory");
            folderDescriptor.setDescription("Choose the directory where flow rules will be stored");

            rulesDirField.addBrowseFolderListener(
                    "Select Rules Directory",
                    "Choose the directory where flow rules will be stored",
                    currentProject(),
                    folderDescriptor,
                    new TextComponentAccessor<JTextField>() {
                        @Override
                        public String getText(JTextField component) {
                            return component.getText();
                        }

                        @Override
                        public void setText(JTextField component, String text) {
                            if (text != null && !text.isEmpty()) {
                                Project project = currentProject();
                                if (project != null && project.getBasePath() != null) {
                                    String basePath = project.getBasePath();
                                    if (text.startsWith(basePath)) {
                                        // 转换为相对路径
                                        String relativePath = basePath.length() == text.length()
                                                ? "."
                                                : text.substring(basePath.length() + 1);
                                        component.setText(relativePath.replace('\\', '/'));
                                    } else {
                                        // 使用绝对路径
                                        component.setText(text.replace('\\', '/'));
                                    }
                                } else {
                                    // 使用绝对路径
                                    component.setText(text.replace('\\', '/'));
                                }
                            }
                        }
                    });

            autoUploadCheck = new JCheckBox("Enable automatic metadata upload");
            autoUploadCheck.addActionListener(e -> updateAutoUploadControls());

            autoUploadIntervalField = new JBTextField();
            autoUploadIntervalField.getEmptyText().setText("60");

            autoRuleSyncCheck = new JCheckBox("Enable automatic rule sync");
            autoRuleSyncCheck.addActionListener(e -> updateAutoRuleSyncControls());

            autoRuleSyncIntervalField = new JBTextField();
            autoRuleSyncIntervalField.getEmptyText().setText("60");

            autoCodeSnapshotUploadCheck = new JCheckBox("Enable automatic code code upload");
            autoCodeSnapshotUploadCheck.addActionListener(e -> updateCodeSnapshotUploadControls());

            autoCodeSnapshotUploadIntervalField = new JBTextField();
            autoCodeSnapshotUploadIntervalField.getEmptyText().setText("30");

            statusIntervalField = new JBTextField();
            statusIntervalField.getEmptyText().setText("5");
            statusIntervalField.getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@NotNull DocumentEvent e) {
                    scheduleAutomaticStatusChecks();
                }
            });

            statusLabel = new JBLabel(STATUS_NOT_CHECKED);
            JButton statusButton = new JButton("Check Connection");
            statusButton.addActionListener(e -> performStatusCheck());
            JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
            statusPanel.add(statusLabel, BorderLayout.CENTER);
            statusPanel.add(statusButton, BorderLayout.EAST);

            jarUploadCheck = new JCheckBox("Enable JAR metadata upload");
            jarUploadCheck.addActionListener(e -> updateJarUploadControls());

            jarSelectionList = new CheckBoxList<>();
            jarSelectionList.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            jarSelectionList.setBorder(JBUI.Borders.empty());
            jarSelectionList.setCheckBoxListListener(new CheckBoxListListener() {
                @Override
                public void checkBoxSelectionChanged(int index, boolean value) {
                    handleJarSelectionChanged(index, value);
                }
            });
            JBScrollPane jarScrollPane = new JBScrollPane(jarSelectionList);
            jarScrollPane.setPreferredSize(new Dimension(-1, 180));

            jarRefreshButton = new JButton("Refresh");
            jarRefreshButton.addActionListener(e -> reloadJarOptions());

            jarListHint = new JBLabel("Jar list not loaded yet");
            jarListHint.setFont(JBFont.small());

            JPanel jarFooter = new JPanel(new BorderLayout(6, 0));
            jarFooter.add(jarListHint, BorderLayout.CENTER);
            jarFooter.add(jarRefreshButton, BorderLayout.EAST);

            JPanel jarPanel = new JPanel(new BorderLayout(0, 6));
            jarPanel.add(jarUploadCheck, BorderLayout.NORTH);
            jarPanel.add(jarScrollPane, BorderLayout.CENTER);
            jarPanel.add(jarFooter, BorderLayout.SOUTH);

            scanIncludesField = new JBTextField();
            scanIncludesField.getEmptyText().setText("e.g. com.example.service, com.example.logic");
            scanExcludesField = new JBTextField();
            scanExcludesField.getEmptyText().setText("e.g. com.example.service.internal");

            JPanel form = FormBuilder.createFormBuilder()
                    .addLabeledComponent("Orchestrator Base URL:", baseUrlField)
                    .addLabeledComponent("Project Key:", projectKeyField)
                    .addComponent(projectKeySourceLabel)
                    .addLabeledComponent("Instance Key:", instancePanel)
                    .addLabeledComponent("Rules Directory:", rulesDirField)
                    .addLabeledComponent("Scan Includes (comma separated):", scanIncludesField)
                    .addLabeledComponent("Scan Excludes (comma separated):", scanExcludesField)
                    .addComponent(autoUploadCheck)
                    .addLabeledComponent("Auto Upload Interval (seconds):", autoUploadIntervalField)
                    .addComponent(autoRuleSyncCheck)
                    .addLabeledComponent("Auto Rule Sync Interval (seconds):", autoRuleSyncIntervalField)
                    .addComponent(autoCodeSnapshotUploadCheck)
                    .addLabeledComponent("Code Snapshot Upload Interval (seconds):",
                            autoCodeSnapshotUploadIntervalField)
                    .addLabeledComponent("Auto Check Interval (s):", statusIntervalField)
                    .addLabeledComponent("Service Status:", statusPanel)
                    .addComponent(jarPanel)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();

            JPanel container = new JPanel(new BorderLayout());
            container.setBorder(JBUI.Borders.empty(12, 14));
            container.add(form, BorderLayout.NORTH);
            panel = container;
        }
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        YgflowSettingsState state = YgflowSettingsState.getInstance();
        if (state == null || panel == null) {
            return false;
        }
        boolean modified = !Objects.equals(trim(baseUrlField.getText()), trim(state.baseUrl))
                || !Objects.equals(trim(instanceKeyField.getText()), trim(state.instanceKey))
                || !Objects.equals(trim(rulesDirField.getText()), trim(state.rulesDir))
                || !Objects.equals(trim(scanIncludesField.getText()), trim(state.scanIncludes))
                || !Objects.equals(trim(scanExcludesField.getText()), trim(state.scanExcludes));
        if (!modified) {
            String intervalText = trim(statusIntervalField.getText());
            try {
                int value = Integer.parseInt(intervalText);
                modified = value != state.statusCheckIntervalSeconds;
            } catch (NumberFormatException ex) {
                modified = true;
            }
        }
        if (!modified) {
            if (autoUploadCheck.isSelected() != state.autoUploadEnabled) {
                modified = true;
            } else {
                String autoInterval = trim(autoUploadIntervalField.getText());
                try {
                    int value = Integer.parseInt(autoInterval);
                    modified = value != state.autoUploadIntervalSeconds;
                } catch (NumberFormatException ex) {
                    modified = true;
                }
            }
        }
        if (!modified) {
            if (autoRuleSyncCheck.isSelected() != state.autoRuleSyncEnabled) {
                modified = true;
            } else {
                String ruleInterval = trim(autoRuleSyncIntervalField.getText());
                try {
                    int value = Integer.parseInt(ruleInterval);
                    modified = value != state.autoRuleSyncIntervalSeconds;
                } catch (NumberFormatException ex) {
                    modified = true;
                }
            }
        }
        if (!modified && jarUploadCheck != null) {
            if (jarUploadCheck.isSelected() != state.jarUploadEnabled) {
                modified = true;
            }
        }
        if (!modified) {
            modified = !jarSelectionState.equals(state.selectedJarCoordinates);
        }
        if (!modified) {
            if (autoCodeSnapshotUploadCheck.isSelected() != state.codeSnapshotAutoUploadEnabled) {
                modified = true;
            } else {
                String text = trim(autoCodeSnapshotUploadIntervalField.getText());
                try {
                    int value = Integer.parseInt(text);
                    modified = value != state.codeSnapshotUploadIntervalSeconds;
                } catch (NumberFormatException ex) {
                    modified = true;
                }
            }
        }
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        YgflowSettingsState state = YgflowSettingsState.getInstance();
        if (state == null) {
            throw new ConfigurationException("YGFlow settings service is unavailable.");
        }
        state.baseUrl = trimOrDefault(baseUrlField.getText(), DEFAULT_BASE_URL);
        state.instanceKey = trimOrDefault(instanceKeyField.getText(), UUID.randomUUID().toString());
        state.rulesDir = trimOrDefault(rulesDirField.getText(), DEFAULT_RULES_DIR);
        state.scanIncludes = trim(scanIncludesField.getText());
        state.scanExcludes = trim(scanExcludesField.getText());
        state.statusCheckIntervalSeconds = validateIntervalSeconds(statusIntervalField.getText());
        state.autoUploadEnabled = autoUploadCheck.isSelected();
        state.autoUploadIntervalSeconds = validateAutoUploadIntervalSeconds(autoUploadIntervalField.getText());
        state.autoRuleSyncEnabled = autoRuleSyncCheck.isSelected();
        state.autoRuleSyncIntervalSeconds = validateAutoRuleSyncIntervalSeconds(autoRuleSyncIntervalField.getText());
        state.jarUploadEnabled = jarUploadCheck != null && jarUploadCheck.isSelected();
        state.selectedJarCoordinates = new HashSet<>(jarSelectionState);
        state.codeSnapshotAutoUploadEnabled = autoCodeSnapshotUploadCheck.isSelected();
        state.codeSnapshotUploadIntervalSeconds = validateCodeSnapshotIntervalSeconds(
                autoCodeSnapshotUploadIntervalField.getText());
        state.ensureInstanceKey();
        scheduleAutomaticStatusChecks();
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            MetadataAutoUploadScheduler scheduler = project.getService(MetadataAutoUploadScheduler.class);
            if (scheduler != null) {
                scheduler.onSettingsChanged();
            }
            CodeSnapshotAutoUploadScheduler codeScheduler = project.getService(CodeSnapshotAutoUploadScheduler.class);
            if (codeScheduler != null) {
                codeScheduler.onSettingsChanged();
            }
            PluginHeartbeatScheduler heartbeatScheduler = project.getService(PluginHeartbeatScheduler.class);
            if (heartbeatScheduler != null) {
                heartbeatScheduler.triggerImmediate();
            }
            RuleAutoSyncScheduler ruleScheduler = project.getService(RuleAutoSyncScheduler.class);
            if (ruleScheduler != null) {
                ruleScheduler.onSettingsChanged();
            }
        }
    }

    @Override
    public void reset() {
        System.out.println("[YGlueSettings] reset() called");
        YgflowSettingsState state = YgflowSettingsState.getInstance();
        if (state == null || panel == null) {
            System.out.println("[YGlueSettings] reset() abort: state or panel null");
            return;
        }
        state.ensureInstanceKey();
        baseUrlField.setText(trim(state.baseUrl));
        DisplayValue projectDisplay = resolveProjectKeyDisplay(state);
        applyProjectKeyDisplay(projectDisplay);
        instanceKeyField.setText(trim(state.instanceKey));
        rulesDirField.setText(trim(state.rulesDir));
        scanIncludesField.setText(trim(state.scanIncludes));
        scanExcludesField.setText(trim(state.scanExcludes));
        autoUploadCheck.setSelected(state.autoUploadEnabled);
        autoUploadIntervalField.setText(String.valueOf(Math.max(5, state.autoUploadIntervalSeconds)));
        autoRuleSyncCheck.setSelected(state.autoRuleSyncEnabled);
        autoRuleSyncIntervalField.setText(String.valueOf(Math.max(5, state.autoRuleSyncIntervalSeconds)));
        statusIntervalField.setText(String.valueOf(Math.max(1, state.statusCheckIntervalSeconds)));
        updateAutoUploadControls();
        updateAutoRuleSyncControls();
        if (jarUploadCheck != null) {
            jarUploadCheck.setSelected(state.jarUploadEnabled);
        }
        jarSelectionState.clear();
        if (state.selectedJarCoordinates != null) {
            jarSelectionState.addAll(state.selectedJarCoordinates);
        }
        reloadJarOptions();
        updateJarUploadControls();
        autoCodeSnapshotUploadCheck.setSelected(state.codeSnapshotAutoUploadEnabled);
        autoCodeSnapshotUploadIntervalField
                .setText(String.valueOf(Math.max(5, state.codeSnapshotUploadIntervalSeconds)));
        updateCodeSnapshotUploadControls();
        markStatusUnknown();
        scheduleAutomaticStatusChecks();
        enqueueStatusCheck(true);
    }

    @Override
    public void disposeUIResources() {
        cancelAutomaticStatusChecks();
        panel = null;
        baseUrlField = null;
        projectKeyField = null;
        projectKeySourceLabel = null;
        instanceKeyField = null;
        rulesDirField = null;
        autoUploadCheck = null;
        autoUploadIntervalField = null;
        autoRuleSyncCheck = null;
        autoRuleSyncIntervalField = null;
        scanIncludesField = null;
        scanExcludesField = null;
        statusIntervalField = null;
        statusLabel = null;
        jarUploadCheck = null;
        autoCodeSnapshotUploadCheck = null;
        autoCodeSnapshotUploadIntervalField = null;
        jarSelectionList = null;
        jarRefreshButton = null;
        jarListHint = null;
        jarSelectionState.clear();
    }

    private void performStatusCheck() {
        enqueueStatusCheck(true);
    }

    private void enqueueStatusCheck(boolean showProgress) {
        if (panel == null) {
            return;
        }
        final String[] endpointHolder = new String[1];
        Runnable readFields = () -> {
            String raw = baseUrlField != null ? baseUrlField.getText() : "";
            endpointHolder[0] = raw == null ? "" : raw;
            if (showProgress && statusLabel != null) {
                statusLabel.setText(STATUS_CHECKING);
            }
        };
        if (ApplicationManager.getApplication().isDispatchThread()) {
            readFields.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(readFields);
        }

        String endpoint = trim(endpointHolder[0]);
        if (endpoint.isBlank()) {
            endpoint = DEFAULT_BASE_URL;
        }
        final String finalEndpoint = endpoint;

        CompletableFuture
                .supplyAsync(() -> checkHealth(finalEndpoint), AppExecutorUtil.getAppExecutorService())
                .whenComplete((result, error) -> {
                    ConnectionCheckResult outcome = error == null
                            ? result
                            : new ConnectionCheckResult(false, "Status: Error - " + error.getMessage());
                    EdtExecutorService.getInstance().execute(() -> {
                        if (statusLabel != null) {
                            statusLabel.setText(outcome.message());
                        }
                    });
                });
    }

    private ConnectionCheckResult checkHealth(String endpoint) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(trimTrailingSlash(endpoint) + "/api/health"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String body = shorten(response.body());
                return new ConnectionCheckResult(false,
                        "Status: Service check failed (HTTP " + status + ")" + (body.isEmpty() ? "" : " - " + body));
            }
            String state = describeStatus(response.body(), "UP");
            return new ConnectionCheckResult(true,
                    "Status: Service " + state + " (HTTP " + status + ")");
        } catch (Exception ex) {
            return new ConnectionCheckResult(false, "Status: Error - " + ex.getMessage());
        }
    }

    private void scheduleAutomaticStatusChecks() {
        cancelAutomaticStatusChecks();
        if (panel == null) {
            return;
        }
        int interval = currentIntervalSeconds();
        statusCheckFuture = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(() -> enqueueStatusCheck(false), 0, interval, TimeUnit.SECONDS);
    }

    private void cancelAutomaticStatusChecks() {
        if (statusCheckFuture != null) {
            statusCheckFuture.cancel(true);
            statusCheckFuture = null;
        }
    }

    private int currentIntervalSeconds() {
        YgflowSettingsState state = YgflowSettingsState.getInstance();
        int fallback = state != null ? Math.max(1, state.statusCheckIntervalSeconds) : 5;
        String text = statusIntervalField != null ? statusIntervalField.getText() : "";
        return parseIntervalValue(text, fallback);
    }

    private int validateIntervalSeconds(String text) throws ConfigurationException {
        String trimmed = trim(text);
        if (trimmed.isEmpty()) {
            return 5;
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 1 || value > 3600) {
                throw new ConfigurationException("Auto check interval must be between 1 and 3600 seconds.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new ConfigurationException("Auto check interval must be a positive number.");
        }
    }

    private int parseIntervalValue(String text, int fallback) {
        String trimmed = trim(text);
        if (trimmed.isEmpty()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(trimmed);
            return value < 1 ? fallback : value;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private DisplayValue resolveProjectKeyDisplay(YgflowSettingsState state) {
        String configured = firstNonBlank(
                System.getProperty("yglue.project"),
                System.getProperty("ygflow.project"),
                System.getenv("YGLUE_PROJECT"),
                System.getenv("YGFLOW_PROJECT"),
                trim(state.projectKey));
        if (!configured.isBlank()) {
            return new DisplayValue(configured, configuredSourceLabel(configured));
        }

        Project project = currentProject();
        if (project != null) {
            String inferred = YgflowConfigurationResolver.inferProjectKey(project);
            if (inferred.isBlank()) {
                String name = project.getName();
                if (name != null) {
                    inferred = name.trim();
                }
            }
            String finalInferred = trim(inferred);
            if (!finalInferred.isBlank()) {
                return new DisplayValue(finalInferred, "Source: current project (" + finalInferred + ")");
            }
        }

        return new DisplayValue("", "Source: not configured");
    }

    private void applyProjectKeyDisplay(DisplayValue display) {
        if (projectKeyField == null || projectKeySourceLabel == null) {
            return;
        }
        projectKeyField.setText(display.value());
        projectKeyField.setToolTipText(display.source());
        projectKeySourceLabel.setText(display.source());
    }

    private String configuredSourceLabel(String value) {
        String trimmed = trim(value);
        if (trimmed.equals(trim(System.getProperty("yglue.project")))) {
            return "Source: system property yglue.project";
        }
        if (trimmed.equals(trim(System.getProperty("ygflow.project")))) {
            return "Source: system property ygflow.project";
        }
        if (trimmed.equals(trim(System.getenv("YGLUE_PROJECT")))) {
            return "Source: env YGLUE_PROJECT";
        }
        if (trimmed.equals(trim(System.getenv("YGFLOW_PROJECT")))) {
            return "Source: env YGFLOW_PROJECT";
        }
        return "Source: configured";
    }

    private Project currentProject() {
        ProjectManager manager = ProjectManager.getInstance();
        Project[] projects = manager.getOpenProjects();
        if (projects.length == 1) {
            Project project = projects[0];
            if (project != null && !project.isDisposed() && !project.isDefault()) {
                return project;
            }
        }
        for (Project project : projects) {
            if (project != null && !project.isDisposed() && !project.isDefault() && project.getBasePath() != null) {
                return project;
            }
        }
        for (Project project : projects) {
            if (project != null && !project.isDisposed() && !project.isDefault()) {
                return project;
            }
        }
        return null;
    }

    private void markStatusUnknown() {
        if (statusLabel != null) {
            statusLabel.setText(STATUS_NOT_CHECKED);
        }
    }

    private void updateAutoUploadControls() {
        if (autoUploadIntervalField != null) {
            autoUploadIntervalField.setEnabled(autoUploadCheck != null && autoUploadCheck.isSelected());
        }
    }

    private void updateAutoRuleSyncControls() {
        if (autoRuleSyncIntervalField != null) {
            autoRuleSyncIntervalField.setEnabled(autoRuleSyncCheck != null && autoRuleSyncCheck.isSelected());
        }
    }

    private void updateCodeSnapshotUploadControls() {
        if (autoCodeSnapshotUploadIntervalField != null) {
            autoCodeSnapshotUploadIntervalField.setEnabled(
                    autoCodeSnapshotUploadCheck != null && autoCodeSnapshotUploadCheck.isSelected());
        }
    }

    private void updateJarUploadControls() {
        boolean enabled = jarUploadCheck != null && jarUploadCheck.isSelected();
        if (jarSelectionList != null) {
            jarSelectionList.setEnabled(enabled);
        }
        if (jarRefreshButton != null) {
            jarRefreshButton.setEnabled(enabled);
        }
        if (jarListHint != null) {
            jarListHint.setEnabled(enabled);
        }
    }

    private void reloadJarOptions() {
        if (jarSelectionList == null) {
            return;
        }
        Project project = currentProject();
        if (project == null) {
            jarSelectionList.clear();
            if (jarListHint != null) {
                jarListHint.setText("No project context available.");
            }
            return;
        }
        if (jarListHint != null) {
            jarListHint.setText("Loading...");
        }
        CompletableFuture
                .supplyAsync(() -> JarDependencyResolver.listProjectJars(project),
                        AppExecutorUtil.getAppExecutorService())
                .whenComplete((list, error) -> {
                    List<JarDependencyResolver.JarInfo> safe = error == null ? list : List.of();
                    EdtExecutorService.getInstance().execute(() -> {
                        if (error != null && jarListHint != null) {
                            jarListHint.setText("加载失败: " + error.getMessage());
                        }
                        applyJarOptions(safe);
                    });
                });
    }

    private void applyJarOptions(List<JarDependencyResolver.JarInfo> options) {
        if (jarSelectionList == null) {
            return;
        }
        jarSelectionList.clear();
        for (JarDependencyResolver.JarInfo info : options) {
            jarSelectionList.addItem(info.id(), info.displayName(), jarSelectionState.contains(info.id()));
        }
        if (jarListHint != null) {
            if (options.isEmpty()) {
                jarListHint.setText("未找到可上传的 Jar");
            } else {
                jarListHint.setText("共 " + options.size() + " 个 Jar 依赖");
            }
        }
        updateJarUploadControls();
    }

    private void handleJarSelectionChanged(int index, boolean value) {
        if (jarSelectionList == null) {
            return;
        }
        var model = jarSelectionList.getModel();
        if (index < 0 || index >= model.getSize()) {
            return;
        }
        String id = jarSelectionList.getItemAt(index);
        if (value) {
            jarSelectionState.add(id);
        } else {
            jarSelectionState.remove(id);
        }
    }

    private int validateAutoUploadIntervalSeconds(String text) throws ConfigurationException {
        String trimmed = trim(text);
        if (trimmed.isEmpty()) {
            return 60;
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 5 || value > 86400) {
                throw new ConfigurationException("Auto upload interval must be between 5 and 86400 seconds.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new ConfigurationException("Auto upload interval must be a positive number.");
        }
    }

    private int validateAutoRuleSyncIntervalSeconds(String text) throws ConfigurationException {
        String trimmed = trim(text);
        if (trimmed.isEmpty()) {
            return 60;
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 5 || value > 86400) {
                throw new ConfigurationException("Auto rule sync interval must be between 5 and 86400 seconds.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new ConfigurationException("Auto rule sync interval must be a positive number.");
        }
    }

    private int validateCodeSnapshotIntervalSeconds(String text) throws ConfigurationException {
        String trimmed = trim(text);
        if (trimmed.isEmpty()) {
            return 30;
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 5 || value > 86400) {
                throw new ConfigurationException("Code code upload interval must be between 5 and 86400 seconds.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new ConfigurationException("Code code upload interval must be a positive number.");
        }
    }

    private record DisplayValue(String value, String source) {
    }

    private record ConnectionCheckResult(boolean ok, String message) {
    }
}

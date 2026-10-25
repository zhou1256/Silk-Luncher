package org.jackhuang.hmcl.silk;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 版本安装窗口：独立小窗，可勾选 Fabric / Forge / NeoForge / OptiFine / Fabric API，
 * 选择与游戏版本对应的加载器版本，展示下载进度，支持随时取消，完成后自动关闭。
 */
public final class SilkInstallWindow {

    /** 可选加载器（按展示顺序）。 */
    private record LoaderOption(String id, String label) {
    }

    private static final List<LoaderOption> LOADERS = List.of(
            new LoaderOption("fabric", "Fabric"),
            new LoaderOption("forge", "Forge"),
            new LoaderOption("neoforge", "NeoForge"),
            new LoaderOption("optifine", "OptiFine"),
            new LoaderOption("fabric-api", "Fabric API"));

    private final String gameVersion;

    private final Map<String, CheckBox> checks = new LinkedHashMap<>();
    private final Map<String, ComboBox<RemoteVersion>> combos = new LinkedHashMap<>();
    private final Set<String> incompatible = new HashSet<>();

    private Stage stage;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button installButton;
    private Button cancelButton;
    private TextField nameField;
    private TaskExecutor currentExecutor;

    public SilkInstallWindow(String gameVersion) {
        this.gameVersion = gameVersion;
    }

    public void show() {
        Stage window = new Stage();
        this.stage = window;

        VBox root = new VBox(16);
        root.getStyleClass().add("page");
        root.setPadding(new Insets(24));

        Label title = new Label("安装 " + gameVersion);
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("可选添加模组加载器，版本会自动匹配游戏版本");
        subtitle.getStyleClass().add("page-subtitle");

        nameField = new TextField(gameVersion);
        nameField.getStyleClass().add("input");
        nameField.setPromptText("版本名称");

        Label nameLabel = new Label("版本名称");
        nameLabel.getStyleClass().add("card-desc");

        VBox loaderBox = new VBox(10);
        for (LoaderOption loader : LOADERS)
            loaderBox.getChildren().add(loaderRow(loader));

        statusLabel = new Label("正在加载可用加载器版本…");
        statusLabel.getStyleClass().add("status-label");

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        installButton = SilkComponents.primaryButton("开始安装");
        installButton.setOnAction(e -> install());

        cancelButton = SilkComponents.ghostButton("取消");
        cancelButton.setDisable(true);
        cancelButton.setOnAction(e -> {
            if (currentExecutor != null)
                currentExecutor.cancel();
        });

        HBox actions = new HBox(12, installButton, cancelButton, statusLabel);
        actions.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(title, subtitle,
                new VBox(6, nameLabel, nameField),
                new VBox(6, new Label("模组加载器"), loaderBox),
                actions, progressBar);

        Scene scene = new Scene(root, 520, 560);
        scene.getStylesheets().add(getClass().getResource("silk.css").toExternalForm());
        window.setTitle("安装 " + gameVersion);
        window.setMinWidth(440);
        window.setMinHeight(460);
        window.setScene(scene);
        window.show();

        loadVersions();
    }

    private Node loaderRow(LoaderOption loader) {
        CheckBox check = new CheckBox(loader.label());
        check.getStyleClass().add("installer-check");
        checks.put(loader.id(), check);

        ComboBox<RemoteVersion> combo = new ComboBox<>();
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setDisable(true);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(RemoteVersion version) {
                return version == null ? "" : version.getSelfVersion();
            }

            @Override
            public RemoteVersion fromString(String s) {
                return null;
            }
        });
        combos.put(loader.id(), combo);

        check.setOnAction(e -> updateConstraints());

        HBox row = new HBox(12, check, combo);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(combo, Priority.ALWAYS);
        return row;
    }

    private void loadVersions() {
        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        DownloadProvider provider = DownloadProviders.getDownloadProvider();
        int[] remaining = {LOADERS.size()};

        for (LoaderOption loader : LOADERS) {
            VersionList<?> versionList = provider.getVersionListById(loader.id());
            Task<?> task = versionList.refreshAsync(gameVersion)
                    .thenSupplyAsync(() -> versionList.getVersions(gameVersion).stream().sorted().collect(Collectors.toList()))
                    .whenComplete(Schedulers.javafx(), (items, exception) -> {
                        CheckBox check = checks.get(loader.id());
                        ComboBox<RemoteVersion> combo = combos.get(loader.id());
                        if (exception == null && !items.isEmpty()) {
                            combo.getItems().setAll(items);
                            combo.getSelectionModel().select(0);
                        } else {
                            incompatible.add(loader.id());
                            check.setDisable(true);
                            check.setText(loader.label() + "（无兼容版本）");
                        }
                        if (--remaining[0] == 0) {
                            statusLabel.setText("请选择要安装的加载器，然后点击「开始安装」");
                        }
                    });
            task.start();
        }
    }

    private void updateConstraints() {
        CheckBox fabric = checks.get("fabric");
        CheckBox forge = checks.get("forge");
        CheckBox neoforge = checks.get("neoforge");
        CheckBox optifine = checks.get("optifine");
        CheckBox fabricApi = checks.get("fabric-api");

        boolean optiFineAllowed = !fabric.isSelected() && !neoforge.isSelected();
        if (!optiFineAllowed)
            optifine.setSelected(false);

        if (fabricApi.isSelected() && !fabric.isSelected())
            fabric.setSelected(true);
        if (!fabric.isSelected())
            fabricApi.setSelected(false);

        fabric.setDisable(incompatible.contains("fabric"));
        forge.setDisable(incompatible.contains("forge"));
        neoforge.setDisable(incompatible.contains("neoforge"));
        optifine.setDisable(incompatible.contains("optifine") || !optiFineAllowed);
        fabricApi.setDisable(incompatible.contains("fabric-api") || !fabric.isSelected());

        for (LoaderOption loader : LOADERS) {
            CheckBox check = checks.get(loader.id());
            ComboBox<RemoteVersion> combo = combos.get(loader.id());
            combo.setDisable(!check.isSelected() || check.isDisabled() || combo.getItems().isEmpty());
        }
    }

    private void install() {
        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        String name = nameField.getText() == null || nameField.getText().isBlank()
                ? gameVersion
                : nameField.getText().trim();

        GameBuilder builder = context.getDependency().gameBuilder();
        builder.name(name).gameVersion(gameVersion);
        for (LoaderOption loader : LOADERS) {
            CheckBox check = checks.get(loader.id());
            if (check.isSelected() && !check.isDisabled()) {
                RemoteVersion selected = combos.get(loader.id()).getSelectionModel().getSelectedItem();
                if (selected != null)
                    builder.version(selected);
            }
        }

        Task<?> task = builder.buildAsync();
        TaskExecutor executor = task.executor();
        currentExecutor = executor;

        installButton.setDisable(true);
        cancelButton.setDisable(false);
        progressBar.setVisible(true);
        statusLabel.setText("正在准备安装…");

        executor.addTaskListener(new TaskListener() {
            @Override
            public void onRunning(Task<?> running) {
                Platform.runLater(() -> {
                    progressBar.progressProperty().unbind();
                    progressBar.progressProperty().bind(running.progressProperty());
                    running.progressProperty().addListener((o, ov, nv) -> {
                        double p = nv.doubleValue();
                        statusLabel.setText(p >= 0 ? prompt(running) + " · " + Math.round(p * 100) + "%" : prompt(running));
                    });
                    statusLabel.setText(prompt(running));
                });
            }

            @Override
            public void onFinished(Task<?> task) {
                Platform.runLater(() -> {
                    if (currentExecutor != null && !currentExecutor.isCancelled()) {
                        statusLabel.setText(prompt(task) + " · 完成");
                    }
                });
            }
        });

        task.onDone().register(event -> Platform.runLater(() -> {
            progressBar.progressProperty().unbind();
            currentExecutor = null;
            installButton.setDisable(false);
            cancelButton.setDisable(true);

            if (event.isFailed()) {
                if (executor.isCancelled()) {
                    progressBar.setProgress(0);
                    statusLabel.setText("已取消安装");
                } else {
                    Exception ex = task.getException();
                    progressBar.setProgress(0);
                    statusLabel.setText(ex != null && ex.getMessage() != null ? "安装失败：" + ex.getMessage() : "安装失败");
                }
            } else {
                progressBar.setProgress(1);
                statusLabel.setText("安装完成：" + name);
                context.getRepository().refreshVersions();
                autoClose();
            }
        }));

        executor.start();
    }

    private String prompt(Task<?> task) {
        String stage = task.getStage() != null ? task.getStage() : task.getInheritedStage();
        if (stage != null) {
            if (stage.startsWith("hmcl.install.game"))
                return "正在下载游戏本体…";
            if (stage.contains("libraries"))
                return "正在下载依赖库…";
            if (stage.contains("assets"))
                return "正在下载资源文件…";
            if (stage.contains("fabric-api"))
                return "正在安装 Fabric API…";
            if (stage.contains("fabric"))
                return "正在安装 Fabric…";
            if (stage.contains("neoforge"))
                return "正在安装 NeoForge…";
            if (stage.contains("optifine"))
                return "正在安装 OptiFine…";
            if (stage.contains("forge"))
                return "正在安装 Forge…";
        }
        String name = task.getName();
        if (name != null && !name.isBlank())
            return "正在处理：" + name;
        return "正在安装…";
    }

    private void autoClose() {
        new Thread(() -> {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException ignored) {
            }
            Platform.runLater(() -> {
                if (stage != null)
                    stage.close();
            });
        }).start();
    }
}

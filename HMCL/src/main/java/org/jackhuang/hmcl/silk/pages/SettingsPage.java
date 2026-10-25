package org.jackhuang.hmcl.silk.pages;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.silk.SilkComponents;
import org.jackhuang.hmcl.silk.SilkContext;
import org.jackhuang.hmcl.silk.SilkPage;
import org.jackhuang.hmcl.util.platform.SystemInfo;

import java.util.List;

/**
 * 设置页：Java 运行时检测、内存自动分配、下载源切换、主题。
 */
public final class SettingsPage implements SilkPage {

    private record SourceOption(String label, DownloadSource source) {
    }

    private static final List<SourceOption> SOURCE_OPTIONS = List.of(
            new SourceOption("自动", DownloadSource.DEFAULT),
            new SourceOption("官方", DownloadSource.OFFICIAL),
            new SourceOption("国内镜像", DownloadSource.MIRROR));

    @Override
    public String getTitle() {
        return "设置";
    }

    @Override
    public Node getView() {
        VBox root = new VBox(24);
        root.getStyleClass().add("page");

        root.getChildren().add(SilkComponents.pageHeader("设置", "Java 运行时 / 内存 / 下载源 / 主题"));

        // Java 运行时
        Label javaStatus = new Label("尚未扫描");
        javaStatus.getStyleClass().add("status-label");
        Button rescan = SilkComponents.ghostButton("重新扫描");
        rescan.setOnAction(e -> refreshJava(javaStatus));
        VBox javaCard = SilkComponents.card("Java 运行时", "启动时自动挑选满足游戏版本要求的 Java",
                SilkComponents.row(12, javaStatus, rescan));

        // 内存
        VBox memCard = buildMemoryCard();

        // 下载源
        VBox sourceCard = SilkComponents.card("下载源", "版本列表与文件下载使用的线路", buildSourceRow());

        // Java 启动参数（按版本）
        VBox jvmArgsCard = buildJvmArgsCard();

        // 主题
        Label themeStatus = new Label("深色 · 蓝紫强调");
        themeStatus.getStyleClass().add("status-label");
        VBox themeCard = SilkComponents.card("主题", "丝滑深色界面", themeStatus);

        Region grid = SilkComponents.cardGrid(javaCard, memCard, sourceCard, jvmArgsCard, themeCard);
        root.getChildren().add(grid);

        refreshJava(javaStatus);
        return root;
    }

    private void refreshJava(Label status) {
        status.setText("正在扫描…");

        Thread thread = new Thread(() -> {
            try {
                SilkContext.ensureJavaInitialized();
                int count = JavaManager.getAllJava().size();
                Platform.runLater(() -> status.setText("已检测到 " + count + " 个 Java 运行时"));
            } catch (Throwable e) {
                Platform.runLater(() -> status.setText("扫描失败：" + e.getMessage()));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private VBox buildMemoryCard() {
        int current = SilkContext.get() == null ? GameSettings.SUGGESTED_MEMORY : SilkContext.get().getMemory();

        TextField field = new TextField(Integer.toString(current));
        field.getStyleClass().add("input");
        field.setMaxWidth(140);

        Label status = new Label("建议值 " + GameSettings.SUGGESTED_MEMORY + " MB · 总内存 " + totalGb() + " GB");
        status.getStyleClass().add("card-desc");

        Button save = SilkComponents.primaryButton("保存");
        save.setOnAction(e -> {
            SilkContext context = SilkContext.get();
            if (context == null)
                return;
            try {
                int mb = Integer.parseInt(field.getText().trim());
                if (mb < 256) {
                    status.setText("内存至少 256 MB");
                    return;
                }
                context.setMemory(mb);
                field.setText(Integer.toString(context.getMemory()));
                status.setText("已保存 " + context.getMemory() + " MB");
            } catch (NumberFormatException ex) {
                status.setText("请输入整数（单位 MB）");
            }
        });

        return SilkComponents.card("内存", "自定义分配给游戏的最大内存",
                SilkComponents.row(12, field, save), status);
    }

    private VBox buildJvmArgsCard() {
        SilkContext context = SilkContext.get();

        ComboBox<String> versionBox = new ComboBox<>();
        versionBox.setPromptText("选择版本");
        versionBox.setMaxWidth(220);

        TextField argsField = new TextField();
        argsField.getStyleClass().add("input");
        argsField.setPromptText("例如：-XX:+UseG1GC -Dfile.encoding=UTF-8");
        argsField.setMaxWidth(Double.MAX_VALUE);

        Label status = new Label("为选中的版本设置额外的 JVM 启动参数");
        status.getStyleClass().add("card-desc");

        Thread loadThread = new Thread(() -> {
            try {
                if (context == null)
                    throw new IllegalStateException("应用尚未初始化");
                context.getRepository().refreshVersions();
                var ids = context.getRepository().getVersions().stream()
                        .map(Version::getId)
                        .sorted()
                        .toList();
                Platform.runLater(() -> versionBox.setItems(FXCollections.observableArrayList(ids)));
            } catch (Exception e) {
                Platform.runLater(() -> status.setText("读取版本列表失败"));
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();

        versionBox.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null && context != null)
                argsField.setText(context.getJvmArgs(nv));
        });

        Button save = SilkComponents.primaryButton("保存");
        save.setOnAction(e -> {
            if (context == null)
                return;
            String version = versionBox.getValue();
            if (version == null || version.isBlank()) {
                status.setText("请先选择版本");
                return;
            }
            context.setJvmArgs(version, argsField.getText());
            status.setText("已保存 " + version + " 的启动参数");
        });

        HBox row = new HBox(12, versionBox, save);
        row.setAlignment(Pos.CENTER_LEFT);

        return SilkComponents.card("Java 启动参数", "为任意 Minecraft 版本自定义 JVM 参数",
                row, argsField, status);
    }

    private static long totalGb() {
        long totalBytes = SystemInfo.getTotalMemorySize();
        return totalBytes > 0 ? totalBytes / (1024L * 1024L * 1024L) : 0;
    }

    private HBox buildSourceRow() {
        ToggleGroup group = new ToggleGroup();
        HBox row = new HBox(8);

        DownloadSource current = SettingsManager.settings().versionListSourceProperty().get();
        for (SourceOption option : SOURCE_OPTIONS) {
            ToggleButton btn = new ToggleButton(option.label());
            btn.getStyleClass().add("filter-button");
            btn.setToggleGroup(group);
            btn.setUserData(option.source());
            if (option.source() == current)
                btn.setSelected(true);
            btn.setOnAction(e -> setSource(option.source()));
            row.getChildren().add(btn);
        }
        return row;
    }

    private void setSource(DownloadSource source) {
        SettingsManager.settings().versionListSourceProperty().set(source);
        SettingsManager.settings().fileDownloadSourceProperty().set(source);
    }
}

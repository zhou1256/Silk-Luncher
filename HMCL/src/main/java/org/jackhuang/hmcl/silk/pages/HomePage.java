package org.jackhuang.hmcl.silk.pages;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.silk.SilkApp;
import org.jackhuang.hmcl.silk.SilkComponents;
import org.jackhuang.hmcl.silk.SilkContext;
import org.jackhuang.hmcl.silk.SilkLauncher;
import org.jackhuang.hmcl.silk.SilkPage;

/**
 * 主页：问候 + 一键启动入口 + 概览卡片。
 */
public final class HomePage implements SilkPage {

    @Override
    public String getTitle() {
        return "主页";
    }

    @Override
    public Node getView() {
        VBox root = new VBox(24);
        root.getStyleClass().add("page");

        root.getChildren().add(SilkComponents.pageHeader("欢迎回来", "选择版本，一键启动"));

        ComboBox<String> versionBox = new ComboBox<>();
        versionBox.setPromptText("选择版本");
        versionBox.setMaxWidth(300);

        Label status = new Label("就绪");
        status.getStyleClass().add("status-label");

        Button launchButton = SilkComponents.primaryButton("启动游戏");
        Button downloadButton = SilkComponents.ghostButton("下载版本");

        // 快捷启动卡片
        VBox launch = SilkComponents.card("快捷启动", "选择版本，直接进入游戏",
                buildLaunchArea(versionBox, launchButton, downloadButton), status);
        launch.getStyleClass().add("hero-card");
        root.getChildren().add(launch);

        // 概览卡片
        Label statVersions = new Label("…");
        Label statAccount = new Label("未登录");
        Label statMemory = new Label("…");

        Region grid = SilkComponents.cardGrid(
                statCard("版本", statVersions),
                statCard("账户", statAccount),
                statCard("内存", statMemory),
                statCard("状态", new Label("就绪"))
        );
        root.getChildren().add(grid);

        launchButton.setOnAction(e -> launchSelected(versionBox, status));
        downloadButton.setOnAction(e -> SilkApp.navigateTo("下载"));

        refreshOverview(versionBox, statVersions, statAccount, statMemory);
        return root;
    }

    private Node buildLaunchArea(ComboBox<String> version, Button launchButton, Button downloadButton) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(version);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(spacer, launchButton, downloadButton);
        return row;
    }

    private VBox statCard(String title, Label value) {
        Label t = new Label(title);
        t.getStyleClass().add("stat-title");
        value.getStyleClass().add("stat-value");

        VBox box = new VBox(6, t, value);
        box.getStyleClass().addAll("card", "stat-card");
        return box;
    }

    private void refreshOverview(ComboBox<String> versionBox, Label statVersions, Label statAccount, Label statMemory) {
        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        Account account = Accounts.getSelectedAccount();
        statAccount.setText(account == null ? "未登录" : account.getProfileName());
        statMemory.setText(context.getMemory() + " MB");

        Thread thread = new Thread(() -> {
            try {
                context.getRepository().refreshVersions();
                var versions = context.getRepository().getVersions().stream()
                        .map(Version::getId)
                        .sorted()
                        .toList();
                Platform.runLater(() -> {
                    statVersions.setText(versions.size() + " 个已安装");
                    String previous = versionBox.getValue();
                    versionBox.setItems(FXCollections.observableArrayList(versions));
                    if (previous != null && versions.contains(previous)) {
                        versionBox.setValue(previous);
                    } else if (!versions.isEmpty()) {
                        versionBox.setValue(versions.get(0));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statVersions.setText("版本读取失败"));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void launchSelected(ComboBox<String> versionBox, Label status) {
        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        Account account = Accounts.getSelectedAccount();
        if (account == null) {
            status.setText("请先在「账户」页添加并选择账户");
            return;
        }

        String version = versionBox.getValue();
        if (version == null || version.isBlank()) {
            status.setText("请先选择要启动的版本");
            return;
        }

        status.setText("正在启动 " + version + " …");
        SilkLauncher.launch(context.getRepository(), context.getDependency(), version, account,
                status::setText,
                status::setText);
    }
}

package org.jackhuang.hmcl.silk.pages;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.silk.SilkApp;
import org.jackhuang.hmcl.silk.SilkComponents;
import org.jackhuang.hmcl.silk.SilkContext;
import org.jackhuang.hmcl.silk.SilkLauncher;
import org.jackhuang.hmcl.silk.SilkPage;

import java.util.Optional;

/**
 * 版本管理页：列出 ~/.minecraft 下已安装的版本，并可一键启动。
 */
public final class VersionPage implements SilkPage {

    @Override
    public String getTitle() {
        return "版本";
    }

    @Override
    public Node getView() {
        VBox root = new VBox(24);
        root.getStyleClass().add("page");

        root.getChildren().add(SilkComponents.pageHeader("版本管理", "管理你的所有 Minecraft 版本"));

        ObservableList<String> allVersions = FXCollections.observableArrayList();
        FilteredList<String> filtered = new FilteredList<>(allVersions);

        ListView<String> list = new ListView<>(filtered);
        list.getStyleClass().add("account-list");
        list.setPlaceholder(SilkComponents.emptyState("还没有版本", "前往「下载」页安装原版、模组加载器或整合包。"));

        Label status = new Label("就绪");
        status.getStyleClass().add("status-label");

        TextField search = new TextField();
        search.getStyleClass().add("input");
        search.setPromptText("搜索已安装版本…");
        search.textProperty().addListener((o, ov, nv) ->
                filtered.setPredicate(id -> nv == null || nv.isBlank()
                        || id.toLowerCase().contains(nv.toLowerCase())));

        Button refresh = SilkComponents.ghostButton("刷新");
        refresh.setOnAction(e -> reload(allVersions, status));

        Button launchButton = SilkComponents.primaryButton("启动所选版本");
        Button deleteButton = SilkComponents.ghostButton("删除所选版本");
        Button installButton = SilkComponents.ghostButton("安装新版本");
        installButton.setOnAction(e -> SilkApp.navigateTo("下载"));

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPromptText("游戏日志将显示在这里");
        logArea.getStyleClass().add("log-area");

        launchButton.setOnAction(e -> launch(list.getSelectionModel().getSelectedItem(), status, logArea));
        deleteButton.setOnAction(e -> deleteSelected(list, allVersions, status));

        VBox card = SilkComponents.card("已安装的版本", "从本地游戏目录读取",
                SilkComponents.gameDirBar(() -> reload(allVersions, status)),
                search,
                SilkComponents.row(12, refresh, launchButton, installButton, deleteButton, status), list, logArea);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);
        root.getChildren().add(card);

        reload(allVersions, status);
        return root;
    }

    private void reload(ObservableList<String> allVersions, Label status) {
        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        allVersions.setAll("加载中…");

        Thread thread = new Thread(() -> {
            try {
                context.getRepository().refreshVersions();
                var versions = context.getRepository().getVersions().stream()
                        .map(org.jackhuang.hmcl.game.Version::getId)
                        .sorted()
                        .toList();
                Platform.runLater(() -> {
                    allVersions.setAll(versions);
                    status.setText("共 " + versions.size() + " 个版本");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    allVersions.setAll("加载失败：" + e.getMessage());
                    status.setText("加载失败");
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void deleteSelected(ListView<String> list, ObservableList<String> allVersions, Label status) {
        String versionId = list.getSelectionModel().getSelectedItem();
        if (versionId == null || versionId.startsWith("加载")) {
            status.setText("请先选择一个已安装的版本");
            return;
        }

        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("删除版本");
        confirm.setHeaderText("确定要删除版本「" + versionId + "」吗？");
        confirm.setContentText("版本文件将移到废纸篓（如系统支持）。");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK)
            return;

        status.setText("正在删除 " + versionId + " …");
        Thread thread = new Thread(() -> {
            boolean ok = context.getRepository().removeVersionFromDisk(versionId);
            Platform.runLater(() -> {
                status.setText(ok ? "已删除 " + versionId : "删除失败：" + versionId);
                reload(allVersions, status);
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void launch(String versionId, Label status, TextArea logArea) {
        if (versionId == null) {
            status.setText("请先选择一个版本");
            return;
        }

        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        Account account = Accounts.getSelectedAccount();
        if (account == null) {
            status.setText("请先在「账户」页添加并选择账户");
            return;
        }

        status.setText("正在启动 " + versionId + " …");
        logArea.clear();
        SilkLauncher.launch(context.getRepository(), context.getDependency(), versionId, account,
                log -> logArea.appendText(log + "\n"),
                result -> {
                    status.setText(result);
                    logArea.appendText(result + "\n");
                });
    }
}

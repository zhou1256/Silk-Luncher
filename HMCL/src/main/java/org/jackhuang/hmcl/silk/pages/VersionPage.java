package org.jackhuang.hmcl.silk.pages;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.silk.SilkApp;
import org.jackhuang.hmcl.silk.SilkComponents;
import org.jackhuang.hmcl.silk.SilkContext;
import org.jackhuang.hmcl.silk.SilkLauncher;
import org.jackhuang.hmcl.silk.SilkPage;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 版本管理页：列出 ~/.minecraft 下已安装的版本，显示加载器 / 模组兼容性，并可一键启动。
 */
public final class VersionPage implements SilkPage {

    /** 单个版本在列表中的展示信息（加载器、模组数量与兼容性在后台线程解析完成）。 */
    private record VersionInfo(String id, String loaderText, int modCount, int incompatibleCount) {
        boolean isPlaceholder() {
            return id.startsWith("加载");
        }

        boolean hasLoader() {
            return loaderText != null && !loaderText.isBlank();
        }

        boolean allCompatible() {
            return incompatibleCount == 0;
        }
    }

    @Override
    public String getTitle() {
        return "版本";
    }

    @Override
    public Node getView() {
        VBox root = new VBox(24);
        root.getStyleClass().add("page");

        root.getChildren().add(SilkComponents.pageHeader("版本管理", "管理你的所有 Minecraft 版本"));

        ObservableList<VersionInfo> allVersions = FXCollections.observableArrayList();
        FilteredList<VersionInfo> filtered = new FilteredList<>(allVersions);

        ListView<VersionInfo> list = new ListView<>(filtered);
        list.getStyleClass().add("account-list");
        list.setPlaceholder(SilkComponents.emptyState("还没有版本", "前往「下载」页安装原版、模组加载器或整合包。"));
        list.setCellFactory(v -> new ListCell<>() {
            private final Label name = new Label();
            private final Label loaderBadge = new Label();
            private final Button more = SilkComponents.ghostButton("更多");
            private final Label meta = new Label();
            private final HBox top = new HBox(8, name, loaderBadge, more);
            private final VBox box = new VBox(3, top, meta);

            {
                name.getStyleClass().add("card-title");
                name.setStyle("-fx-font-size: 14px;");
                HBox.setHgrow(name, Priority.ALWAYS);
                top.setAlignment(Pos.CENTER_LEFT);
                loaderBadge.getStyleClass().add("badge");
                more.setStyle("-fx-padding: 5 12; -fx-font-size: 12px;");
                meta.getStyleClass().add("card-desc");
                meta.setStyle("-fx-font-size: 11px;");
                more.setOnAction(e -> {
                    VersionInfo info = getItem();
                    if (info != null && !info.isPlaceholder())
                        showMoreMenu(info.id(), more);
                });
            }

            @Override
            protected void updateItem(VersionInfo info, boolean empty) {
                super.updateItem(info, empty);
                if (empty || info == null) {
                    setGraphic(null);
                    return;
                }
                name.setText(info.id());
                if (info.hasLoader()) {
                    loaderBadge.setText(info.loaderText());
                    loaderBadge.setVisible(true);
                    loaderBadge.setManaged(true);
                    meta.setText(info.modCount() + " 个模组 · "
                            + (info.allCompatible() ? "全部兼容" : info.incompatibleCount() + " 个不兼容"));
                    meta.setVisible(true);
                    meta.setManaged(true);
                } else {
                    loaderBadge.setVisible(false);
                    loaderBadge.setManaged(false);
                    meta.setVisible(false);
                    meta.setManaged(false);
                }
                setGraphic(box);
            }
        });

        Label status = new Label("就绪");
        status.getStyleClass().add("status-label");

        TextField search = new TextField();
        search.getStyleClass().add("input");
        search.setPromptText("搜索已安装版本…");
        search.textProperty().addListener((o, ov, nv) ->
                filtered.setPredicate(info -> nv == null || nv.isBlank()
                        || info.id().toLowerCase().contains(nv.toLowerCase())));

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

    /** 点击「更多」后，在按钮下方弹出带动画的文件夹菜单。 */
    private void showMoreMenu(String versionId, Node anchor) {
        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        Popup popup = new Popup();
        popup.setAutoHide(true);

        VBox panel = new VBox(4);
        panel.getStyleClass().add("more-menu");
        panel.getChildren().addAll(
                menuItem(popup, "📂 打开 mod 文件夹", () -> FXUtils.openFolder(context.getRepository().getModsDirectory(versionId))),
                menuItem(popup, "📁 打开版本文件夹", () -> FXUtils.openFolder(context.getRepository().getVersionRoot(versionId))),
                menuItem(popup, "✨ 打开光影文件夹", () -> FXUtils.openFolder(context.getRepository().getRunDirectory(versionId).resolve("shaderpacks"))),
                menuItem(popup, "📦 打开资源包文件夹", () -> FXUtils.openFolder(context.getRepository().getResourcePackDirectory(versionId)))
        );

        popup.getContent().add(panel);

        panel.setOpacity(0);
        panel.setTranslateY(-6);
        popup.show(anchor, 0, anchor.getBoundsInLocal().getHeight() + 2);
        if (panel.getScene() != null)
            panel.getScene().getStylesheets().add(
                    VersionPage.class.getResource("/org/jackhuang/hmcl/silk/silk.css").toExternalForm());

        FadeTransition fade = new FadeTransition(Duration.millis(160), panel);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(160), panel);
        slide.setFromY(-6);
        slide.setToY(0);

        fade.play();
        slide.play();
    }

    private Button menuItem(Popup popup, String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("more-menu-item");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(e -> {
            popup.hide();
            action.run();
        });
        return button;
    }

    private void reload(ObservableList<VersionInfo> allVersions, Label status) {
        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        allVersions.setAll(new VersionInfo("加载中…", "", 0, 0));

        Thread thread = new Thread(() -> {
            try {
                context.getRepository().refreshVersions();
                List<String> ids = context.getRepository().getVersions().stream()
                        .map(Version::getId)
                        .sorted()
                        .toList();
                List<VersionInfo> infos = new ArrayList<>();
                for (String id : ids)
                    infos.add(loadVersionInfo(context.getRepository(), id));
                Platform.runLater(() -> {
                    allVersions.setAll(infos);
                    status.setText("共 " + infos.size() + " 个版本");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    allVersions.setAll(new VersionInfo("加载失败：" + e.getMessage(), "", 0, 0));
                    status.setText("加载失败");
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /** 解析单个版本的加载器与模组兼容性；任何异常都回退为“无加载器”，不影响列表显示。 */
    private VersionInfo loadVersionInfo(DefaultGameRepository repository, String id) {
        try {
            ModManager manager = new ModManager(repository, id);
            List<LocalModFile> mods = manager.getLocalFiles();
            Set<ModLoaderType> loaders = manager.getLibraryAnalyzer().getModLoaders();

            if (loaders.isEmpty())
                return new VersionInfo(id, "", mods.size(), 0);

            String loaderText = loaders.stream()
                    .map(VersionPage::loaderName)
                    .sorted()
                    .collect(Collectors.joining(" · "));
            if (loaders.contains(ModLoaderType.FABRIC) && hasFabricApi(mods))
                loaderText += " · Fabric API";

            String mcVersion = null;
            try {
                mcVersion = repository.getGameVersion(id).orElse(null);
            } catch (Exception ignored) {
            }

            int incompatible = 0;
            for (LocalModFile mod : mods) {
                if (!isCompatible(mod, loaders, mcVersion))
                    incompatible++;
            }
            return new VersionInfo(id, loaderText, mods.size(), incompatible);
        } catch (Exception e) {
            return new VersionInfo(id, "", 0, 0);
        }
    }

    private static String loaderName(ModLoaderType type) {
        return switch (type) {
            case FORGE -> "Forge";
            case NEO_FORGE -> "NeoForge";
            case FABRIC -> "Fabric";
            case LEGACY_FABRIC -> "Legacy Fabric";
            case QUILT -> "Quilt";
            case LITE_LOADER -> "LiteLoader";
            case CLEANROOM -> "Cleanroom";
            default -> "未知加载器";
        };
    }

    private static boolean hasFabricApi(List<LocalModFile> mods) {
        for (LocalModFile mod : mods) {
            String modId = mod.getId();
            if (modId != null && (modId.equals("fabric-api") || modId.equals("fabric_api")))
                return true;
            String fileName = mod.getFileName();
            if (fileName != null && fileName.toLowerCase().startsWith("fabric-api"))
                return true;
        }
        return false;
    }

    private static boolean isCompatible(LocalModFile mod, Set<ModLoaderType> versionLoaders, String mcVersion) {
        ModLoaderType modLoader = mod.getModLoaderType();
        boolean loaderOk = modLoader == null
                || modLoader == ModLoaderType.UNKNOWN
                || versionLoaders.contains(modLoader);

        String modMcVersion = mod.getGameVersion();
        boolean versionOk = modMcVersion == null || modMcVersion.isBlank()
                || mcVersion == null || mcVersion.isBlank()
                || modMcVersion.equals(mcVersion);

        return loaderOk && versionOk;
    }

    private void deleteSelected(ListView<VersionInfo> list, ObservableList<VersionInfo> allVersions, Label status) {
        VersionInfo info = list.getSelectionModel().getSelectedItem();
        if (info == null || info.isPlaceholder()) {
            status.setText("请先选择一个已安装的版本");
            return;
        }
        String versionId = info.id();

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

    private void launch(VersionInfo info, Label status, TextArea logArea) {
        if (info == null || info.isPlaceholder()) {
            status.setText("请先选择一个版本");
            return;
        }
        String versionId = info.id();

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

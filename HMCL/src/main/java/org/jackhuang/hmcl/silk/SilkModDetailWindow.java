package org.jackhuang.hmcl.silk;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模组详情窗口：独立小窗展示 Fabric / Forge 两栏版本，支持任意版本下载并显示进度。
 */
public final class SilkModDetailWindow {

    private final RemoteAddon addon;
    private final RemoteAddonRepository repository;
    private final DownloadProvider downloadProvider;

    private Label statusLabel;
    private ProgressBar progressBar;
    private Button cancelButton;
    private Button openFolderButton;
    private Path lastDownloadDir;
    private VBox fabricList;
    private VBox forgeList;
    private TaskExecutor currentExecutor;

    public SilkModDetailWindow(RemoteAddon addon, RemoteAddonRepository repository) {
        this.addon = addon;
        this.repository = repository;
        this.downloadProvider = DownloadProviders.getDownloadProvider();
    }

    public void show() {
        Stage stage = new Stage();

        VBox root = new VBox(16);
        root.getStyleClass().add("page");
        root.setPadding(new Insets(24));

        Label title = new Label(addon.title() == null ? "" : addon.title());
        title.getStyleClass().add("page-title");
        title.setWrapText(true);

        Label author = new Label(addon.author() == null ? "" : addon.author());
        author.getStyleClass().add("card-desc");

        Label desc = new Label(addon.description() == null ? "" : addon.description());
        desc.getStyleClass().add("card-desc");
        desc.setWrapText(true);

        statusLabel = new Label("正在加载版本…");
        statusLabel.getStyleClass().add("status-label");

        fabricList = new VBox(8);
        forgeList = new VBox(8);

        VBox fabricCol = column("Fabric", fabricList);
        VBox forgeCol = column("Forge", forgeList);
        HBox columns = new HBox(16, fabricCol, forgeCol);
        HBox.setHgrow(fabricCol, Priority.ALWAYS);
        HBox.setHgrow(forgeCol, Priority.ALWAYS);
        VBox.setVgrow(columns, Priority.ALWAYS);

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        cancelButton = SilkComponents.ghostButton("取消");
        cancelButton.setVisible(false);
        cancelButton.setOnAction(e -> {
            if (currentExecutor != null)
                currentExecutor.cancel();
        });

        openFolderButton = SilkComponents.ghostButton("📂 打开文件夹");
        openFolderButton.setVisible(false);
        openFolderButton.setOnAction(e -> {
            if (lastDownloadDir != null)
                FXUtils.openFolder(lastDownloadDir);
        });

        HBox bottom = new HBox(12, progressBar, cancelButton, openFolderButton);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        bottom.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(title, author, desc, statusLabel, columns, bottom);

        Scene scene = new Scene(root, 760, 580);
        scene.getStylesheets().add(getClass().getResource("silk.css").toExternalForm());
        stage.setTitle("模组 · " + (addon.title() == null ? "" : addon.title()));
        stage.setMinWidth(560);
        stage.setMinHeight(420);
        stage.setScene(scene);
        stage.show();

        loadVersions();
    }

    private VBox column(String name, VBox list) {
        Label header = new Label(name);
        header.getStyleClass().add("card-title");

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("mod-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        return new VBox(12, header, scroll);
    }

    private void loadVersions() {
        Task.supplyAsync(() -> {
            List<RemoteAddon.Version> versions = addon.data().loadVersions(repository, downloadProvider).collect(Collectors.toList());
            versions.sort((a, b) -> {
                if (a.datePublished() == null && b.datePublished() == null)
                    return 0;
                if (a.datePublished() == null)
                    return 1;
                if (b.datePublished() == null)
                    return -1;
                return b.datePublished().compareTo(a.datePublished());
            });
            return versions;
        }).whenComplete(Schedulers.javafx(), (versions, exception) -> {
            if (exception != null) {
                statusLabel.setText("加载版本失败：" + exception.getMessage());
                return;
            }

            int fabric = 0;
            int forge = 0;
            for (RemoteAddon.Version version : versions) {
                if (version.loaders().contains(ModLoaderType.FABRIC)) {
                    fabricList.getChildren().add(versionRow(version));
                    fabric++;
                }
                if (version.loaders().contains(ModLoaderType.FORGE)) {
                    forgeList.getChildren().add(versionRow(version));
                    forge++;
                }
            }
            if (fabric == 0)
                fabricList.getChildren().add(hint("暂无 Fabric 版本"));
            if (forge == 0)
                forgeList.getChildren().add(hint("暂无 Forge 版本"));
            statusLabel.setText("共 " + versions.size() + " 个版本 · 点击「下载」保存到 mods 目录");
        }).start();
    }

    private Node versionRow(RemoteAddon.Version version) {
        Label name = new Label(version.name());
        name.getStyleClass().add("card-title");
        name.setStyle("-fx-font-size: 13px;");

        Label meta = new Label(String.join(" · ", version.gameVersions()));
        meta.getStyleClass().add("card-desc");
        meta.setStyle("-fx-font-size: 11px;");

        VBox text = new VBox(3, name, meta);

        Button download = SilkComponents.ghostButton("下载");
        download.setOnAction(e -> download(version));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, text, spacer, download);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mod-version-row");
        return row;
    }

    private Node hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("status-label");
        return label;
    }

    private static String resolveFilename(RemoteAddon.Version version) {
        RemoteAddon.File file = version.file();
        String filename = file != null ? file.filename() : null;
        if (filename == null || filename.isBlank()) {
            String url = file != null ? file.url() : null;
            if (url != null && !url.isBlank()) {
                int slash = url.lastIndexOf('/');
                filename = slash >= 0 ? url.substring(slash + 1) : url;
                int query = filename.indexOf('?');
                if (query >= 0)
                    filename = filename.substring(0, query);
            } else {
                filename = version.name() + ".jar";
            }
        }
        return filename;
    }

    /** 下载目标目录与选择原因的说明。 */
    private record TargetDir(Path dir, String reason) {
    }

    /** 一次扫描得到的版本加载器与模组列表。 */
    private record VersionModInfo(Set<ModLoaderType> loaders, List<LocalModFile> mods) {
    }

    /**
     * 根据模组适配的游戏版本与加载器确定下载目录：
     * 只有同时满足「MC 版本匹配」「加载器匹配」「(Fabric 时) 已装 Fabric API」的已安装版本
     * 才会放入其隔离的 mods 目录（versions/<id>/mods）；否则暂存到共享目录 .minecraft/mods。
     */
    private TargetDir resolveTargetModsDir(RemoteAddon.Version version) {
        SilkContext context = SilkContext.get();
        if (context == null)
            return null;

        DefaultGameRepository repository = context.getRepository();
        List<String> modGameVersions = version.gameVersions();
        List<ModLoaderType> modLoaders = version.loaders();

        if (modGameVersions == null || modGameVersions.isEmpty())
            return new TargetDir(repository.getBaseDirectory().resolve("mods"), "模组未声明适配的游戏版本，已暂存到 .minecraft/mods");

        try {
            repository.refreshVersions();
        } catch (Exception ignored) {
        }

        String bestId = null;
        GameVersionNumber bestVersion = null;
        for (Version installed : repository.getVersions()) {
            Optional<String> gameVersion;
            try {
                gameVersion = repository.getGameVersion(installed);
            } catch (Exception e) {
                continue;
            }
            if (gameVersion.isEmpty() || !modGameVersions.contains(gameVersion.get()))
                continue;

            VersionModInfo info = scanVersion(repository, installed.getId());
            if (info.loaders().isEmpty())
                continue;

            boolean loaderOk = modLoaders == null || modLoaders.isEmpty()
                    || modLoaders.stream().anyMatch(info.loaders()::contains);
            if (!loaderOk)
                continue;

            boolean needsFabricApi = modLoaders != null
                    && modLoaders.contains(ModLoaderType.FABRIC)
                    && info.loaders().contains(ModLoaderType.FABRIC);
            if (needsFabricApi && !hasFabricApi(info.mods()))
                continue;

            GameVersionNumber parsed = GameVersionNumber.asGameVersion(gameVersion.get());
            if (bestVersion == null || parsed.compareTo(bestVersion) > 0) {
                bestVersion = parsed;
                bestId = installed.getId();
            }
        }

        if (bestId != null)
            return new TargetDir(repository.getModsDirectory(bestId), "已放入版本「" + bestId + "」的 mod 文件夹");

        return new TargetDir(repository.getBaseDirectory().resolve("mods"),
                "未找到加载器匹配的版本，已暂存到 .minecraft/mods");
    }

    private VersionModInfo scanVersion(DefaultGameRepository repository, String id) {
        try {
            ModManager manager = new ModManager(repository, id);
            List<LocalModFile> mods = manager.getLocalFiles();
            return new VersionModInfo(manager.getLibraryAnalyzer().getModLoaders(), mods);
        } catch (Exception e) {
            return new VersionModInfo(Set.of(), List.of());
        }
    }

    private boolean hasFabricApi(List<LocalModFile> mods) {
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

    private void download(RemoteAddon.Version version) {
        if (currentExecutor != null) {
            statusLabel.setText("已有下载进行中，请稍候…");
            return;
        }

        TargetDir target = resolveTargetModsDir(version);
        if (target == null)
            return;
        Path modsDir = target.dir();
        try {
            Files.createDirectories(modsDir);
        } catch (IOException ignored) {
        }
        Path dest = modsDir.resolve(resolveFilename(version));

        openFolderButton.setVisible(false);
        lastDownloadDir = null;

        FileDownloadTask task = new FileDownloadTask(
                downloadProvider.injectURLWithCandidates(version.file().url()),
                dest,
                version.file().getIntegrityCheck());
        task.setName(version.name());

        progressBar.setVisible(true);
        cancelButton.setVisible(true);
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.setText("正在下载 " + version.name() + " …");

        TaskExecutor executor = task.executor();
        currentExecutor = executor;
        task.onDone().register(event -> Platform.runLater(() -> {
            progressBar.progressProperty().unbind();
            currentExecutor = null;
            cancelButton.setVisible(false);

            if (event.isFailed()) {
                Exception ex = task.getException();
                progressBar.setProgress(0);
                statusLabel.setText(ex != null && ex.getMessage() != null ? "下载失败：" + ex.getMessage() : "下载失败");
            } else {
                progressBar.setProgress(1);
                lastDownloadDir = modsDir;
                openFolderButton.setVisible(true);
                statusLabel.setText(target.reason() + "：" + modsDir);
            }
        }));
        executor.start();
    }
}

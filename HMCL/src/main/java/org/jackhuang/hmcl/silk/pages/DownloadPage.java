package org.jackhuang.hmcl.silk.pages;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.silk.SilkComponents;
import org.jackhuang.hmcl.silk.SilkContext;
import org.jackhuang.hmcl.silk.SilkInstallWindow;
import org.jackhuang.hmcl.silk.SilkPage;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 下载中心：原版 Minecraft 列表与安装，按版本类型分类（正式版 / 预览版 / 愚人节版 / 远古版）。
 */
public final class DownloadPage implements SilkPage {

    /** 版本分类。 */
    private enum Category {
        RELEASE("正式版"),
        SNAPSHOT("预览版"),
        APRIL_FOOLS("愚人节版"),
        OLD("远古版"),
        ALL("全部");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private final ObservableList<RemoteVersion> allVersions = FXCollections.observableArrayList();
    private final FilteredList<RemoteVersion> filtered = new FilteredList<>(allVersions);

    private Category category = Category.RELEASE;
    private String query = "";

    /** 下载源选项。 */
    private record SourceOption(String label, DownloadSource source) {
    }

    private static final List<SourceOption> SOURCE_OPTIONS = List.of(
            new SourceOption("自动", DownloadSource.DEFAULT),
            new SourceOption("官方", DownloadSource.OFFICIAL),
            new SourceOption("国内镜像", DownloadSource.MIRROR));

    @Override
    public String getTitle() {
        return "下载";
    }

    @Override
    public Node getView() {
        VBox root = new VBox(24);
        root.getStyleClass().add("page");

        root.getChildren().add(SilkComponents.pageHeader("下载中心", "原版 / 模组加载器 / 整合包"));

        Label status = new Label("尚未加载版本列表");
        status.getStyleClass().add("status-label");

        HBox categoryRow = buildCategoryRow(status);

        ListView<RemoteVersion> list = new ListView<>(filtered);
        list.getStyleClass().add("account-list");
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(RemoteVersion version, boolean empty) {
                super.updateItem(version, empty);
                setText(empty || version == null ? null : version.getSelfVersion());
            }
        });

        Button refresh = SilkComponents.ghostButton("刷新列表");
        refresh.setOnAction(e -> refresh(status));

        Button install = SilkComponents.primaryButton("安装所选版本");
        install.setOnAction(e -> install(list.getSelectionModel().getSelectedItem(), status));

        TextField search = new TextField();
        search.getStyleClass().add("input");
        search.setPromptText("搜索版本号（如 1.20）…");
        search.setText(query);
        search.textProperty().addListener((o, ov, nv) -> {
            query = nv == null ? "" : nv.trim();
            applyFilter();
            updateStatus(status);
        });

        VBox card = SilkComponents.card("原版 Minecraft", "从官方 / 镜像源下载任意版本",
                SilkComponents.gameDirBar(() -> status.setText("游戏目录已切换")),
                buildSourceRow(status),
                categoryRow, search, SilkComponents.row(12, refresh, install), status, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);
        root.getChildren().add(card);

        refresh(status);
        return root;
    }

    private HBox buildCategoryRow(Label status) {
        ToggleGroup group = new ToggleGroup();
        HBox row = new HBox(8);
        row.getStyleClass().add("filter-row");

        for (Category value : Category.values()) {
            ToggleButton btn = new ToggleButton(value.label);
            btn.getStyleClass().add("filter-button");
            btn.setToggleGroup(group);
            btn.setUserData(value);
            if (value == category)
                btn.setSelected(true);
            btn.setOnAction(e -> setCategory(value, status));
            row.getChildren().add(btn);
        }
        return row;
    }

    private HBox buildSourceRow(Label status) {
        Label prefix = new Label("下载源");
        prefix.getStyleClass().add("card-desc");

        ToggleGroup group = new ToggleGroup();
        HBox buttons = new HBox(8);

        DownloadSource current = SettingsManager.settings().versionListSourceProperty().get();
        for (SourceOption option : SOURCE_OPTIONS) {
            ToggleButton btn = new ToggleButton(option.label());
            btn.getStyleClass().add("filter-button");
            btn.setToggleGroup(group);
            btn.setUserData(option.source());
            if (option.source() == current)
                btn.setSelected(true);
            btn.setOnAction(e -> setSource(option.source(), status));
            buttons.getChildren().add(btn);
        }

        HBox row = new HBox(12, prefix, buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void setSource(DownloadSource source, Label status) {
        SettingsManager.settings().versionListSourceProperty().set(source);
        SettingsManager.settings().fileDownloadSourceProperty().set(source);
        refresh(status);
    }

    private void setCategory(Category next, Label status) {
        this.category = next;
        applyFilter();
        updateStatus(status);
    }

    private void applyFilter() {
        filtered.setPredicate(version -> matches(category, version) && matchesQuery(version));
    }

    private boolean matchesQuery(RemoteVersion version) {
        if (query == null || query.isBlank())
            return true;
        String self = version.getSelfVersion() == null ? "" : version.getSelfVersion();
        return self.toLowerCase().contains(query.toLowerCase());
    }

    private static boolean matches(Category category, RemoteVersion version) {
        RemoteVersion.Type type = version.getVersionType();
        return switch (category) {
            case RELEASE -> type == RemoteVersion.Type.RELEASE;
            case SNAPSHOT -> (type == RemoteVersion.Type.SNAPSHOT
                    || type == RemoteVersion.Type.PENDING
                    || type == RemoteVersion.Type.UNOBFUSCATED)
                    && !isAprilFools(version);
            case APRIL_FOOLS -> type == RemoteVersion.Type.SNAPSHOT && isAprilFools(version);
            case OLD -> type == RemoteVersion.Type.OLD;
            case ALL -> true;
        };
    }

    private static boolean isAprilFools(RemoteVersion version) {
        return GameVersionNumber.asGameVersion(version.getGameVersion()).isAprilFools();
    }

    private void refresh(Label status) {
        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        status.setText("正在加载版本列表…");

        DownloadProvider provider = DownloadProviders.getDownloadProvider();
        VersionList<?> versionList = provider.getVersionListById("game");

        Task<?> task = versionList.refreshAsync("")
                .thenSupplyAsync(() -> versionList.getVersions("").stream().sorted().collect(Collectors.toList()))
                .whenComplete(Schedulers.javafx(), (items, exception) -> {
                    if (exception == null) {
                        allVersions.setAll(items);
                        applyFilter();
                        updateStatus(status);
                    } else {
                        status.setText("加载失败：" + exception.getMessage());
                    }
                });
        task.start();
    }

    private void updateStatus(Label status) {
        status.setText(category.label + " · " + filtered.size() + " 个版本");
    }

    private void install(RemoteVersion version, Label status) {
        if (version == null)
            return;

        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        status.setText("请在弹出的安装窗口中完成配置");
        new SilkInstallWindow(version.getGameVersion()).show();
    }
}

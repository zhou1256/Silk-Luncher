package org.jackhuang.hmcl.silk.pages;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.silk.SilkComponents;
import org.jackhuang.hmcl.silk.SilkContext;
import org.jackhuang.hmcl.silk.SilkModDetailWindow;
import org.jackhuang.hmcl.silk.SilkPage;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;

import java.util.List;

/**
 * 模组下载页：在 Modrinth / CurseForge 之间切换，搜索并打开模组详情窗口。
 */
public final class ModDownloadPage implements SilkPage {

    /** 模组来源选项。 */
    private record SourceOption(String label, RemoteAddonRepository repo) {
    }

    private static final List<SourceOption> SOURCES = List.of(
            new SourceOption("Modrinth", RemoteAddon.Source.MODRINTH.modRepo),
            new SourceOption("CurseForge", RemoteAddon.Source.CURSEFORGE.modRepo));

    private final ObservableList<RemoteAddon> items = FXCollections.observableArrayList();
    private RemoteAddonRepository repository = SOURCES.get(0).repo();
    private String lastQuery = "";

    @Override
    public String getTitle() {
        return "模组";
    }

    @Override
    public Node getView() {
        VBox root = new VBox(24);
        root.getStyleClass().add("page");

        root.getChildren().add(SilkComponents.pageHeader("模组下载", "从 Modrinth / CurseForge 搜索并下载模组"));

        Label status = new Label("就绪");
        status.getStyleClass().add("status-label");

        HBox sourceRow = buildSourceRow(status);
        HBox searchRow = buildSearchRow(status);

        ListView<RemoteAddon> list = new ListView<>(items);
        list.getStyleClass().add("account-list");
        list.setCellFactory(v -> new ListCell<>() {
            private final Label title = new Label();
            private final Label meta = new Label();
            private final VBox box = new VBox(4, title, meta);

            {
                title.getStyleClass().add("card-title");
                title.setStyle("-fx-font-size: 14px;");
                meta.getStyleClass().add("card-desc");
            }

            @Override
            protected void updateItem(RemoteAddon addon, boolean empty) {
                super.updateItem(addon, empty);
                if (empty || addon == null) {
                    setGraphic(null);
                    return;
                }
                title.setText(addon.title() == null ? "" : addon.title());
                meta.setText(summarize(addon));
                setGraphic(box);
            }
        });

        Button detail = SilkComponents.ghostButton("查看详情");
        detail.setOnAction(e -> openDetail(list.getSelectionModel().getSelectedItem(), status));

        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2)
                openDetail(list.getSelectionModel().getSelectedItem(), status);
        });

        VBox card = SilkComponents.card("模组", "搜索后双击，或选中后点击「查看详情」",
                sourceRow, searchRow, SilkComponents.row(12, detail, status), list);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);
        root.getChildren().add(card);

        search(lastQuery, status);
        return root;
    }

    private static String summarize(RemoteAddon addon) {
        StringBuilder sb = new StringBuilder();
        if (addon.author() != null && !addon.author().isBlank())
            sb.append(addon.author()).append(" · ");
        String desc = addon.description() == null ? "" : addon.description().replaceAll("\\R", " ").trim();
        if (desc.length() > 120)
            desc = desc.substring(0, 120) + "…";
        sb.append(desc);
        return sb.toString();
    }

    private HBox buildSourceRow(Label status) {
        Label prefix = new Label("来源");
        prefix.getStyleClass().add("card-desc");

        ToggleGroup group = new ToggleGroup();
        HBox buttons = new HBox(8);

        for (SourceOption option : SOURCES) {
            ToggleButton btn = new ToggleButton(option.label());
            btn.getStyleClass().add("filter-button");
            btn.setToggleGroup(group);
            btn.setUserData(option.repo());
            if (option.repo() == repository)
                btn.setSelected(true);
            btn.setOnAction(e -> {
                repository = option.repo();
                search(lastQuery, status);
            });
            buttons.getChildren().add(btn);
        }

        HBox row = new HBox(12, prefix, buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildSearchRow(Label status) {
        TextField field = new TextField();
        field.getStyleClass().add("input");
        field.setPromptText("搜索模组名称…");
        field.setText(lastQuery);
        HBox.setHgrow(field, Priority.ALWAYS);

        Button search = SilkComponents.primaryButton("搜索");
        search.setOnAction(e -> search(field.getText(), status));
        field.setOnAction(e -> search(field.getText(), status));

        return new HBox(12, field, search);
    }

    private void search(String query, Label status) {
        this.lastQuery = query;
        SilkContext context = SilkContext.get();
        if (context == null)
            return;

        DownloadProvider provider = DownloadProviders.getDownloadProvider();
        RemoteAddonRepository repo = repository;

        status.setText("正在搜索…");
        Task.supplyAsync(() -> repo.search(provider, "", null, 0, 50, query,
                        RemoteAddonRepository.SortType.POPULARITY, RemoteAddonRepository.SortOrder.DESC)
                .getResults().toList())
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        items.setAll(result);
                        status.setText("共 " + result.size() + " 个结果");
                    } else {
                        items.clear();
                        status.setText("搜索失败：" + exception.getMessage());
                    }
                }).start();
    }

    private void openDetail(RemoteAddon addon, Label status) {
        if (addon == null) {
            status.setText("请先选择一个模组");
            return;
        }
        new SilkModDetailWindow(addon, repository).show();
    }
}

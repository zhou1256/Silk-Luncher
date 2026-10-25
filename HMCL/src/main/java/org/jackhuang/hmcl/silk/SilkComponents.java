package org.jackhuang.hmcl.silk;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;

/**
 * Silk UI 复用的轻量组件工厂，保持各页面风格一致。
 */
public final class SilkComponents {

    private SilkComponents() {
    }

    /** 页面头部：大标题 + 副标题。 */
    public static VBox pageHeader(String title, String subtitle) {
        Label t = new Label(title);
        t.getStyleClass().add("page-title");

        Label s = new Label(subtitle);
        s.getStyleClass().add("page-subtitle");

        VBox box = new VBox(6, t, s);
        box.getStyleClass().add("page-header");
        return box;
    }

    /** 内容卡片：标题 + 说明 + 自定义内容区。 */
    public static VBox card(String title, String desc, Node... children) {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");

        if (title != null && !title.isEmpty()) {
            Label t = new Label(title);
            t.getStyleClass().add("card-title");
            box.getChildren().add(t);
        }
        if (desc != null && !desc.isEmpty()) {
            Label d = new Label(desc);
            d.getStyleClass().add("card-desc");
            d.setWrapText(true);
            box.getChildren().add(d);
        }
        box.getChildren().addAll(children);
        return box;
    }

    /** 卡片网格：横向自动换行。 */
    public static Region cardGrid(Node... cards) {
        javafx.scene.layout.FlowPane grid = new javafx.scene.layout.FlowPane();
        grid.getStyleClass().add("card-grid");
        grid.setHgap(16);
        grid.setVgap(16);
        grid.getChildren().addAll(cards);
        return grid;
    }

    /** 主按钮（强调色）。 */
    public static Button primaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("btn", "btn-primary");
        return b;
    }

    /** 次级按钮（描边/幽灵）。 */
    public static Button ghostButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("btn", "btn-ghost");
        return b;
    }

    /** 水平排列一组控件，居左。 */
    public static HBox row(double spacing, Node... children) {
        HBox box = new HBox(spacing);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(children);
        return box;
    }

    /** 游戏目录选择条：显示当前目录，点击「更改目录」弹出选择器，切换后触发回调。 */
    public static HBox gameDirBar(Runnable onChanged) {
        Label prefix = new Label("游戏目录");
        prefix.getStyleClass().add("card-desc");

        Label path = new Label();
        path.getStyleClass().add("status-label");
        path.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        path.setMaxWidth(380);
        SilkContext context = SilkContext.get();
        if (context != null)
            path.setText(context.getGameDir().toString());

        Button change = ghostButton("更改目录");
        change.setOnAction(e -> {
            if (context == null)
                return;

            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择游戏目录");
            File selected = chooser.showDialog(change.getScene().getWindow());
            if (selected == null)
                return;

            context.setGameDir(selected.toPath());
            path.setText(selected.getAbsolutePath());
            onChanged.run();
        });

        HBox row = new HBox(12, prefix, path, change);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** 空状态占位：用于尚未实现功能的页面区域。 */
    public static VBox emptyState(String title, String desc) {
        Label t = new Label(title);
        t.getStyleClass().add("empty-title");

        Label d = new Label(desc);
        d.getStyleClass().add("empty-desc");
        d.setWrapText(true);

        VBox box = new VBox(8, t, d);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}

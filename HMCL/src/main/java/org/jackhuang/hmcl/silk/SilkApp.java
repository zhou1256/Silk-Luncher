package org.jackhuang.hmcl.silk;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.silk.pages.AccountPage;
import org.jackhuang.hmcl.silk.pages.DownloadPage;
import org.jackhuang.hmcl.silk.pages.HomePage;
import org.jackhuang.hmcl.silk.pages.ModDownloadPage;
import org.jackhuang.hmcl.silk.pages.SettingsPage;
import org.jackhuang.hmcl.silk.pages.VersionPage;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Silk UI —— 新的丝滑界面入口。
 * 深色侧边栏导航 + 内容区，页面对应 org.jackhuang.hmcl.silk.pages 下的各个 SilkPage。
 */
public final class SilkApp extends Application {

    private static final Map<String, SilkPage> NAV = new LinkedHashMap<>();
    private static final Map<String, ToggleButton> NAV_BUTTONS = new LinkedHashMap<>();

    static {
        NAV.put("主页", new HomePage());
        NAV.put("版本", new VersionPage());
        NAV.put("下载", new DownloadPage());
        NAV.put("模组", new ModDownloadPage());
        NAV.put("账户", new AccountPage());
        NAV.put("设置", new SettingsPage());
    }

    /** 当前内容区，供页面内的快捷按钮跳转使用。 */
    private static StackPane CONTENT;

    @Override
    public void start(Stage stage) {
        initializeCore();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        StackPane content = new StackPane();
        content.getStyleClass().add("content");

        root.setLeft(buildSidebar(content));
        root.setCenter(content);

        showPage(content, NAV.values().iterator().next());

        Scene scene = new Scene(root, 1180, 740);
        scene.getStylesheets().add(getClass().getResource("silk.css").toExternalForm());

        stage.setTitle("Silk Launcher");
        stage.setMinWidth(940);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildSidebar(StackPane content) {
        CONTENT = content;

        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(236);

        Label logo = new Label("Silk");
        logo.getStyleClass().add("logo");

        ToggleGroup group = new ToggleGroup();
        VBox nav = new VBox();
        nav.getStyleClass().add("nav");

        boolean first = true;
        for (Map.Entry<String, SilkPage> entry : NAV.entrySet()) {
            ToggleButton btn = new ToggleButton(entry.getKey());
            btn.getStyleClass().add("nav-button");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setToggleGroup(group);
            btn.setSelected(first);
            first = false;
            btn.setOnAction(e -> navigateTo(entry.getKey()));
            NAV_BUTTONS.put(entry.getKey(), btn);
            nav.getChildren().add(btn);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("GPL-3.0 · 基于 HMCL 核心");
        footer.getStyleClass().add("footer");

        sidebar.getChildren().addAll(logo, nav, spacer, footer);
        return sidebar;
    }

    private static void showPage(StackPane content, SilkPage page) {
        Node view = page.getView();
        content.getChildren().setAll(view);

        TranslateTransition tt = new TranslateTransition(Duration.millis(260), view);
        tt.setFromY(18);
        tt.setToY(0);

        FadeTransition ft = new FadeTransition(Duration.millis(320), view);
        ft.setFromValue(0);
        ft.setToValue(1);

        tt.play();
        ft.play();
    }

    /** 按标题跳转到某个页面（供页面内快捷按钮调用）。 */
    public static void navigateTo(String title) {
        SilkPage page = NAV.get(title);
        if (page == null || CONTENT == null)
            return;
        showPage(CONTENT, page);
        ToggleButton btn = NAV_BUTTONS.get(title);
        if (btn != null)
            btn.setSelected(true);
    }

    private void initializeCore() {
        try {
            SilkContext.initialize();
        } catch (Throwable e) {
            // 初始化失败时降级：UI 仍然显示，但账户/版本等功能不可用
            System.err.println("[Silk] 初始化 HMCL 核心服务失败: " + e);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        anchorLocalHome();
        launch(args);
    }

    /**
     * 打包成 .app 后由 Finder 双击启动时，JVM 的 user.dir 是 "/"（App Translocation 时则是
     * /private/var/folders/... 只读临时目录），会让 HMCL_LOCAL_HOME 落到 /.hmcl 等不可写位置，
     * 导致配置、缓存、Java 自动下载等全部无法持久化。这里在 Metadata 初始化之前把本地目录
     * 锚定到 ~/Library/Application Support/hmcl（与 HMCL_USER_HOME 一致），保证打包 app 与
     * 开发模式（runSilk，workingDir 为源码目录）行为一致。
     */
    private static void anchorLocalHome() {
        if (System.getProperty("hmcl.dir") != null || System.getenv("HMCL_LOCAL_HOME") != null)
            return;

        String userDir = System.getProperty("user.dir", "");
        boolean finderLaunch = "/".equals(userDir)
                || userDir.startsWith("/private/var/folders/")
                || userDir.startsWith("/var/folders/");
        if (!finderLaunch)
            return;

        Path home = Path.of(System.getProperty("user.home", "."));
        Path local = home.resolve("Library").resolve("Application Support").resolve("hmcl");
        System.setProperty("hmcl.dir", local.toAbsolutePath().normalize().toString());
    }
}

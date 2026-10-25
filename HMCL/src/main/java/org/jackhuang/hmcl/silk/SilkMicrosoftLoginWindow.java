package org.jackhuang.hmcl.silk;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;

import java.util.function.Consumer;

/**
 * 微软账号登录窗口：使用设备代码流（device code），在浏览器输入代码完成验证。
 * 事件监听挂在 Accounts.OAUTH_CALLBACK 上，登录成功后把账户加入 Accounts 并选中。
 */
public final class SilkMicrosoftLoginWindow {

    private Stage stage;
    private Label codeLabel;
    private Label status;

    /** 保持强引用，供 registerWeak 使用，避免监听器被提前回收。 */
    private final Consumer<OAuthServer.GrantDeviceCodeEvent> onDeviceCode = event ->
            Platform.runLater(() -> {
                codeLabel.setText(event.getUserCode());
                status.setText("在浏览器打开 " + event.getVerificationUri() + " 并输入上面的代码");
            });

    private final Consumer<OAuthServer.LoginCompletedDeviceCodeEvent> onCompleted = event ->
            Platform.runLater(() -> status.setText("验证成功，正在获取账户信息…"));

    public void show() {
        Stage window = new Stage();
        this.stage = window;

        VBox root = new VBox(16);
        root.getStyleClass().add("page");
        root.setPadding(new Insets(24));

        Label title = new Label("微软登录");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("正版验证 · 使用设备代码登录");
        subtitle.getStyleClass().add("page-subtitle");

        codeLabel = new Label("—");
        codeLabel.getStyleClass().add("hero-version");
        codeLabel.setStyle("-fx-font-size: 30px; -fx-font-family: monospace;");

        Label codeHint = new Label("设备代码");
        codeHint.getStyleClass().add("card-desc");

        status = new Label("正在请求登录…");
        status.getStyleClass().add("status-label");
        status.setWrapText(true);

        Button copy = SilkComponents.ghostButton("复制代码");
        copy.setOnAction(e -> {
            String code = codeLabel.getText();
            if (code != null && !code.isBlank() && !"—".equals(code)) {
                Clipboard.getSystemClipboard().setContent(
                        java.util.Collections.singletonMap(DataFormat.PLAIN_TEXT, code));
                status.setText("已复制代码");
            }
        });

        Button cancel = SilkComponents.ghostButton("关闭");
        cancel.setOnAction(e -> window.close());

        HBox actions = new HBox(12, copy, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(title, subtitle,
                new VBox(6, codeHint, codeLabel), status, actions);

        Scene scene = new Scene(root, 460, 320);
        scene.getStylesheets().add(getClass().getResource("silk.css").toExternalForm());
        window.setTitle("微软登录");
        window.setMinWidth(420);
        window.setMinHeight(300);
        window.setScene(scene);
        window.show();

        startLogin();
    }

    private void startLogin() {
        if (Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            status.setText("未配置微软客户端 ID：请在构建时设置 hmcl.microsoft.auth.id");
            return;
        }

        Accounts.OAUTH_CALLBACK.onGrantDeviceCode.registerWeak(onDeviceCode);
        Accounts.OAUTH_CALLBACK.onLoginCompletedDeviceCode.registerWeak(onCompleted);

        Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.DEVICE))
                .whenComplete(Schedulers.javafx(), (MicrosoftAccount account, Exception exception) -> {
                    if (exception == null) {
                        Accounts.getAccounts().add(account);
                        Accounts.setSelectedAccount(account);
                        status.setText("登录成功：" + account.getProfileName());
                        autoClose();
                    } else {
                        status.setText("登录失败：" + Accounts.localizeErrorMessage(exception));
                    }
                })
                .start();
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

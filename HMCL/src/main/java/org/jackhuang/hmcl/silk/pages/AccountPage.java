package org.jackhuang.hmcl.silk.pages;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.silk.SilkComponents;
import org.jackhuang.hmcl.silk.SilkMicrosoftLoginWindow;
import org.jackhuang.hmcl.silk.SilkPage;

/**
 * 账户页：离线登录已可用；微软登录后续接入。
 */
public final class AccountPage implements SilkPage {

    @Override
    public String getTitle() {
        return "账户";
    }

    @Override
    public Node getView() {
        VBox root = new VBox(24);
        root.getStyleClass().add("page");

        root.getChildren().add(SilkComponents.pageHeader("账户", "微软正版 / 离线登录"));

        // 已添加的账户列表
        ListView<Account> list = new ListView<>();
        list.getStyleClass().add("account-list");
        list.setItems(Accounts.getAccounts());
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Account account, boolean empty) {
                super.updateItem(account, empty);
                if (empty || account == null) {
                    setText(null);
                    return;
                }
                setText(account.getProfileName());
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o, oldValue, cur) -> {
            if (cur != null)
                Accounts.setSelectedAccount(cur);
        });

        Account selected = Accounts.getSelectedAccount();
        if (selected != null)
            list.getSelectionModel().select(selected);

        Button deleteButton = SilkComponents.ghostButton("删除所选账户");
        deleteButton.setOnAction(e -> deleteSelected(list));

        VBox listCard = SilkComponents.card("已添加的账户", "选择要用于启动游戏的账户",
                SilkComponents.row(12, deleteButton), list);
        VBox.setVgrow(list, Priority.ALWAYS);
        root.getChildren().add(listCard);
        VBox.setVgrow(listCard, Priority.ALWAYS);

        // 添加账户
        TextField nameField = new TextField();
        nameField.setPromptText("离线用户名");
        nameField.setMaxWidth(240);
        nameField.getStyleClass().add("input");

        Button offlineButton = SilkComponents.primaryButton("离线登录");
        offlineButton.setOnAction(e -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (name.isEmpty())
                return;

            OfflineAccount account = Accounts.FACTORY_OFFLINE.create(
                    name, OfflineAccountFactory.getUUIDFromUserName(name));
            Accounts.getAccounts().add(account);
            Accounts.setSelectedAccount(account);
            list.getSelectionModel().select(account);
            nameField.clear();
        });

        Button microsoftButton = SilkComponents.ghostButton("微软登录");
        microsoftButton.setOnAction(e -> new SilkMicrosoftLoginWindow().show());

        VBox addCard = SilkComponents.card("添加账户", "离线模式无需密码；微软登录用于正版验证",
                SilkComponents.row(12, nameField, offlineButton, microsoftButton));
        root.getChildren().add(addCard);

        return root;
    }

    private void deleteSelected(ListView<Account> list) {
        Account selected = list.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        Accounts.getAccounts().remove(selected);
        Account nowSelected = Accounts.getSelectedAccount();
        if (nowSelected != null)
            list.getSelectionModel().select(nowSelected);
    }
}

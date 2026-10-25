package org.jackhuang.hmcl.silk;

import javafx.scene.Node;

/**
 * Silk UI 页面统一接口：每个导航项对应一个页面，返回其根节点。
 */
public interface SilkPage {

    /**
     * 页面标题（侧边栏与顶部标题共用）。
     */
    String getTitle();

    /**
     * 页面的根节点，由 SilkApp 放进内容区并做切换动画。
     */
    Node getView();
}

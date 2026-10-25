# 貢獻指南

<!-- #BEGIN LANGUAGE_SWITCHER -->
[English](Contributing.md) | **中文** ([简体](Contributing_zh.md), **繁體**)
<!-- #END LANGUAGE_SWITCHER -->

## 關於 Silk

Silk Launcher 是一款自製的 **macOS Minecraft 啟動器**。它基於 [HMCL](https://github.com/HMCL-dev/HMCL) 3.16.4 的原始碼修改而來，復用了 HMCL 的核心邏輯（帳戶 / 下載 / 啟動 / 版本管理），並重新打造了一套**絲滑的深色 JavaFX 介面**。

主要功能：

- **版本管理**：新增、刪除、啟動遊戲版本，一鍵切換遊戲目錄
- **下載中心**：下載各版本 Minecraft，支援安裝 Forge / Fabric / NeoForge / OptiFine / Fabric API
- **模組下載**：搜尋並下載模組（Modrinth 來源）
- **帳戶系統**：支援離線登入
- **Java 管理**：自動偵測本機 Java，缺失時從國內鏡像（BMCLAPI）自動下載

> 本專案僅重做前端介面，核心能力歸功於 HMCL 及其貢獻者。

## 使用方法

### 下載安裝

前往 [Releases](https://github.com/zhou1256/Silk-Luncher/releases) 下載最新的 `Silk-macos-aarch64.zip`（Apple Silicon / arm64）。

解壓後把 `Silk.app` 拖到「應用程式」。首次開啟若提示「無法驗證開發者」，請在 Finder 中右鍵 `Silk.app` → 開啟，再確認一次即可。

### 基本使用

1. 開啟 Silk 後，在「下載」頁選擇要安裝的 Minecraft 版本；
2. 可勾選 Forge / Fabric 等模組載入器，點擊「開始安裝」；
3. 安裝完成後，在「版本」頁選擇已安裝的版本並點擊「啟動」；
4. 遊戲目錄、記憶體大小等可在設定中修改，會自動保存。

## 從原始碼構建

### 環境需求

- macOS
- JDK 17 或更高版本

### 本地執行（開發模式）

```shell
./gradlew :HMCL:runSilk
```

### 打包 macOS .app

```shell
./gradlew :HMCL:createSilkApp
```

打包產物位於 `HMCL/build/silk-app/Silk.app`。

## 參與貢獻

歡迎提交 Issue 回報 bug 或提出建議，也歡迎提交 Pull Request。

在提交程式碼前，請確保：

1. 不要破壞既有功能；
2. 盡量保持改動範圍最小，避免順手重構無關程式碼；
3. 改動後自測一遍核心流程（下載、安裝、啟動）。

本專案基於 HMCL 修改，遵循 [GPL-3.0](../LICENSE) 協議發布，請遵守相關條款。

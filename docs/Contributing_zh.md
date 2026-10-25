# 贡献指南

<!-- #BEGIN LANGUAGE_SWITCHER -->
[English](Contributing.md) | **中文** (**简体**, [繁體](Contributing_zh_Hant.md))
<!-- #END LANGUAGE_SWITCHER -->

## 关于 Silk

Silk Launcher 是一款自制的 **macOS Minecraft 启动器**。它基于 [HMCL](https://github.com/HMCL-dev/HMCL) 3.16.4 的源代码修改而来，复用了 HMCL 的核心逻辑（账户 / 下载 / 启动 / 版本管理），并重新打造了一套**丝滑的深色 JavaFX 界面**。

主要功能：

- **版本管理**：添加、删除、启动游戏版本，一键切换游戏目录
- **下载中心**：下载各版本 Minecraft，支持安装 Forge / Fabric / NeoForge / OptiFine / Fabric API
- **模组下载**：搜索并下载模组（Modrinth 源）
- **账户系统**：支持离线登录
- **Java 管理**：自动检测本机 Java，缺失时从国内镜像（BMCLAPI）自动下载

> 本项目仅重做前端 UI，核心能力归功于 HMCL 及其贡献者。

## 使用方法

### 下载安装

前往 [Releases](https://github.com/zhou1256/Silk-Luncher/releases) 下载最新的 `Silk-macos-aarch64.zip`（Apple Silicon / arm64）。

解压后把 `Silk.app` 拖到「应用程序」。首次打开若提示「无法验证开发者」，请在 Finder 中右键 `Silk.app` → 打开，再确认一次即可。

### 基本使用

1. 打开 Silk 后，在「下载」页选择要安装的 Minecraft 版本；
2. 可勾选 Forge / Fabric 等模组加载器，点击「开始安装」；
3. 安装完成后，在「版本」页选择已安装的版本并点击「启动」；
4. 游戏目录、内存大小等可在设置中修改，会自动持久化。

## 从源码构建

### 环境需求

- macOS
- JDK 17 或更高版本

### 本地运行（开发模式）

```shell
./gradlew :HMCL:runSilk
```

### 打包 macOS .app

```shell
./gradlew :HMCL:createSilkApp
```

打包产物位于 `HMCL/build/silk-app/Silk.app`。

## 参与贡献

欢迎提交 Issue 反馈 bug 或提出建议，也欢迎提交 Pull Request。

在提交代码前，请确保：

1. 不要破坏已有功能；
2. 尽量保持改动范围最小，避免顺手重构无关代码；
3. 改动后自测一遍核心流程（下载、安装、启动）。

本项目基于 HMCL 修改，遵循 [GPL-3.0](../LICENSE) 协议发布，请遵守相关条款。

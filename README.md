# Silk Launcher

一个自制的 macOS Minecraft 启动器，基于 [HMCL](https://github.com/HMCL-dev/HMCL) 的源代码修改而来，换上了全新的丝滑深色界面。

> 本项目复用 HMCL 3.16.4 的核心（账户 / 下载 / 启动 / 版本管理），只重做了前端 UI。感谢 HMCL 及其贡献者。

## 功能

- **版本管理**：添加、删除、启动游戏版本，一键切换游戏目录
- **下载中心**：下载各版本 Minecraft，安装 Forge / Fabric / NeoForge / OptiFine / Fabric API
- **模组下载**：搜索并下载模组（Modrinth 源）
- **账户系统**：支持离线登录与微软账号登录
- **Java 管理**：自动检测本机 Java，没有时从国内镜像（BMCLAPI）自动下载
- **全新界面**：丝滑的深色 JavaFX UI

## 下载

前往 [Releases](https://github.com/zhouyuncheng15/Silk-Luncher/releases) 下载最新的 `Silk-macos-aarch64.zip`（Apple Silicon / arm64）。

解压后把 `Silk.app` 拖到「应用程序」。首次打开若提示无法验证，请在 Finder 中右键 → 打开。

## 从源码构建

```bash
# 本地运行（开发模式）
./gradlew :HMCL:runSilk

# 打包 macOS .app（产物在 HMCL/build/silk-app/Silk.app）
./gradlew :HMCL:createSilkApp
```

## 已知问题

目前仍处于早期开发阶段，还有很多 bug 未修复，欢迎提 Issue 反馈。

## 开源协议

基于 [HMCL](https://github.com/HMCL-dev/HMCL) 修改，遵循 [GPL-3.0](LICENSE) 协议发布。

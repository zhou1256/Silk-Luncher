# Contributing Guide

<!-- #BEGIN LANGUAGE_SWITCHER -->
**English** | 中文 ([简体](Contributing_zh.md), [繁體](Contributing_zh_Hant.md))
<!-- #END LANGUAGE_SWITCHER -->

## About Silk

Silk Launcher is a self-made **Minecraft launcher for macOS**. It is built on top of the [HMCL](https://github.com/HMCL-dev/HMCL) 3.16.4 source code, reusing HMCL's core logic (accounts / download / launch / version management) while rebuilding a **smooth, dark JavaFX UI** from scratch.

Key features:

- **Version management**: add, remove, and launch game versions, and switch the game directory with one click
- **Download center**: download Minecraft versions and install Forge / Fabric / NeoForge / OptiFine / Fabric API
- **Mod download**: search and download mods (Modrinth source)
- **Account system**: offline login support
- **Java management**: auto-detect the local Java runtime, and download one from a Chinese mirror (BMCLAPI) when missing

> This project only reworks the front-end UI. The core capabilities belong to HMCL and its contributors.

## Usage

### Download & install

Download the latest `Silk-macos-aarch64.zip` (Apple Silicon / arm64) from [Releases](https://github.com/zhou1256/Silk-Luncher/releases).

Unzip it and drag `Silk.app` into `Applications`. If macOS warns that the developer cannot be verified on first launch, right-click `Silk.app` in Finder → Open, then confirm once more.

### Basic usage

1. Open Silk and pick a Minecraft version on the "Download" page;
2. Optionally check Forge / Fabric loaders and click "Install";
3. After installation, select the installed version on the "Versions" page and click "Launch";
4. The game directory and memory size can be changed in settings and are persisted automatically.

## Build from source

### Requirements

- macOS
- JDK 17 or higher

### Run locally (development mode)

```shell
./gradlew :HMCL:runSilk
```

### Package the macOS .app

```shell
./gradlew :HMCL:createSilkApp
```

The output is located at `HMCL/build/silk-app/Silk.app`.

## Contributing

Issues (bug reports and suggestions) and Pull Requests are both welcome.

Before submitting code, please make sure to:

1. Do not break existing features;
2. Keep the change scope minimal and avoid refactoring unrelated code;
3. Self-test the core flows (download, install, launch) after your changes.

This project is based on HMCL and released under [GPL-3.0](../LICENSE). Please follow its terms.

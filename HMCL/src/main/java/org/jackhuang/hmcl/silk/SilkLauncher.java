package org.jackhuang.hmcl.silk;

import javafx.application.Platform;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.game.HMCLGameLauncher;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.util.function.Consumer;

/**
 * 启动游戏的封装：在后台线程登录 → 解析版本 → 补齐依赖 → 拉起 Minecraft 进程。
 */
public final class SilkLauncher {

    private SilkLauncher() {
    }

    /**
     * @param repository 游戏仓库（读取版本 / 启动目录）
     * @param dependency 依赖管理器（用于补齐缺失的库与资源）
     * @param versionId  要启动的版本 id
     * @param account    用于登录的账户（离线 / 正版）
     * @param onLog      日志回调（已在 JavaFX 线程）
     * @param onResult   结束回调（已在 JavaFX 线程），成功/失败都会调用
     */
    public static void launch(DefaultGameRepository repository, DefaultDependencyManager dependency,
                              String versionId, Account account,
                              Consumer<String> onLog, Consumer<String> onResult) {
        Thread thread = new Thread(() -> {
            try {
                AuthInfo authInfo = account.logIn();
                Version version = repository.getResolvedVersion(versionId);

                // 补齐缺失的版本 jar / 库 / 资源（已存在则跳过）
                Platform.runLater(() -> onLog.accept("正在准备游戏文件…"));
                Task<?> completion = dependency.checkGameCompletionAsync(version, true);
                TaskExecutor executor = completion.executor();
                if (!executor.test()) {
                    Exception e = executor.getException();
                    throw e != null ? e : new IllegalStateException("下载游戏依赖失败");
                }

                JavaRuntime java = selectJava(repository, version, onLog);
                int maxMemory = currentMemory();

                Platform.runLater(() -> onLog.accept("已选择 Java " + java.getVersion()
                        + " · 分配内存 " + maxMemory + " MB"));

                LaunchOptions options = new LaunchOptions.Builder()
                        .setGameDir(repository.getBaseDirectory())
                        .setJava(java)
                        .setMaxMemory(maxMemory)
                        .setWidth(854)
                        .setHeight(480)
                        .setVersionName(versionId)
                        .create();

                ProcessListener listener = new ProcessListener() {
                    @Override
                    public void onLog(String log, boolean isErrorStream) {
                        Platform.runLater(() -> onLog.accept(log));
                    }

                    @Override
                    public void onExit(int exitCode, ProcessListener.ExitType exitType) {
                        Platform.runLater(() -> onResult.accept("游戏已退出（代码 " + exitCode + "，类型 " + exitType + "）"));
                    }
                };

                Platform.runLater(() -> onLog.accept("正在启动 " + versionId + " …"));
                new HMCLGameLauncher(repository, version, authInfo, options, listener).launch();
            } catch (Exception e) {
                Platform.runLater(() -> onResult.accept("启动失败：" + e.getMessage()));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /** 读取用户配置的内存（MB），未配置时回退到自动建议值。 */
    private static int currentMemory() {
        SilkContext context = SilkContext.get();
        if (context == null)
            return GameSettings.SUGGESTED_MEMORY;
        int memory = context.getMemory();
        return memory > 0 ? memory : GameSettings.SUGGESTED_MEMORY;
    }

    /**
     * 按版本自动挑选最合适的 Java：优先使用满足该游戏版本最低要求的运行时；
     * 找不到时自动下载符合版本要求的 Java（默认走国内镜像源）。
     */
    private static JavaRuntime selectJava(DefaultGameRepository repository, Version version, Consumer<String> onLog) {
        try {
            SilkContext.ensureJavaInitialized();
        } catch (Throwable e) {
            // 扫描失败不致命，下面仍会尝试自动下载
        }

        GameVersionNumber gameVersion = repository.getGameVersion(version)
                .map(GameVersionNumber::asGameVersion)
                .orElse(null);

        try {
            JavaRuntime java = JavaManager.findSuitableJava(gameVersion, version);
            if (java != null)
                return java;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 未检测到可用的 Java，按版本要求自动下载
        GameJavaVersion required = version.getJavaVersion();
        if (required == null)
            required = GameJavaVersion.getMinimumJavaVersion(gameVersion);
        if (required == null)
            required = GameJavaVersion.LATEST;
        final GameJavaVersion javaToDownload = required;

        Platform.runLater(() -> onLog.accept("未检测到可用的 Java，正在自动下载 Java " + javaToDownload.majorVersion() + " …"));

        Task<JavaRuntime> downloadTask = JavaManager.getDownloadJavaTask(
                DownloadProviders.getDownloadProvider(), org.jackhuang.hmcl.util.platform.Platform.SYSTEM_PLATFORM, javaToDownload);
        TaskExecutor executor = downloadTask.executor();
        if (!executor.test()) {
            Exception e = executor.getException();
            throw new IllegalStateException("自动下载 Java 失败", e != null ? e : new RuntimeException("未知错误"));
        }
        JavaRuntime downloaded = downloadTask.getResult();
        if (downloaded == null)
            throw new IllegalStateException("自动下载 Java 失败");
        return downloaded;
    }
}

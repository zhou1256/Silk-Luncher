package org.jackhuang.hmcl.silk;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.HMCLCacheRepository;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.ProxyManager;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.util.CacheRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Silk UI 的共享上下文：负责初始化 HMCL 核心服务，并暴露游戏仓库与下载管理器。
 * 游戏目录可运行时切换（下载页 / 版本页），选择结果会持久化。
 */
public final class SilkContext {

    private static SilkContext instance;

    private static final Path CONFIG_FILE = Metadata.HMCL_LOCAL_HOME.resolve("silk.properties");
    private static final String KEY_GAME_DIR = "game-dir";
    private static final String KEY_MEMORY = "memory";

    private Path gameDir;
    private int memory;
    private DefaultGameRepository repository;

    private SilkContext() {
        gameDir = loadGameDir();
        memory = loadMemory();
        repository = new DefaultGameRepository(gameDir);
    }

    /**
     * 初始化 HMCL 核心服务（只应调用一次）。
     */
    public static synchronized void initialize() throws IOException {
        if (instance != null)
            return;

        SettingsManager.init();
        DownloadProviders.init();
        ProxyManager.init();
        Accounts.init();
        CacheRepository.setInstance(HMCLCacheRepository.REPOSITORY);

        instance = new SilkContext();
    }

    public static SilkContext get() {
        return instance;
    }

    /**
     * 懒加载扫描本机 Java（只执行一次，线程安全）。
     * 供「启动时自动选择 Java」与「设置页 Java 状态」共用。
     */
    public static void ensureJavaInitialized() {
        if (JavaManager.isInitialized())
            return;
        synchronized (SilkContext.class) {
            if (!JavaManager.isInitialized())
                JavaManager.initialize();
        }
    }

    public DefaultGameRepository getRepository() {
        return repository;
    }

    /** 当前游戏目录。 */
    public Path getGameDir() {
        return gameDir;
    }

    /** 分配给游戏的最大内存（MB）。 */
    public int getMemory() {
        return memory;
    }

    /** 修改分配给游戏的最大内存（MB）并持久化。 */
    public synchronized void setMemory(int memoryMb) {
        this.memory = Math.max(256, memoryMb);
        saveMemory(this.memory);
    }

    /**
     * 切换到新的游戏目录：确保目录存在、重建仓库并持久化。
     */
    public synchronized void setGameDir(Path dir) {
        Path normalized = dir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized);
        } catch (IOException ignored) {
        }

        this.gameDir = normalized;
        this.repository = new DefaultGameRepository(normalized);
        saveGameDir(normalized);
    }

    /** 使用当前下载源创建一个依赖管理器，用于下载 / 安装版本。 */
    public DefaultDependencyManager getDependency() {
        return new DefaultDependencyManager(repository, DownloadProviders.getDownloadProvider(), HMCLCacheRepository.REPOSITORY);
    }

    private static Properties readProps() {
        Properties props = new Properties();
        if (Files.isRegularFile(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException ignored) {
            }
        }
        return props;
    }

    private static void writeProps(Consumer<Properties> mutate) {
        Properties props = readProps();
        mutate.accept(props);
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                props.store(out, "Silk launcher settings");
            }
        } catch (IOException ignored) {
            // 持久化失败不影响运行
        }
    }

    private static Path loadGameDir() {
        Path fallback = Path.of(System.getProperty("user.home"), ".minecraft");
        String value = readProps().getProperty(KEY_GAME_DIR);
        if (value == null || value.isBlank())
            return fallback;
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static void saveGameDir(Path dir) {
        writeProps(props -> props.setProperty(KEY_GAME_DIR, dir.toString()));
    }

    private static int loadMemory() {
        String value = readProps().getProperty(KEY_MEMORY);
        if (value == null || value.isBlank())
            return GameSettings.SUGGESTED_MEMORY;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : GameSettings.SUGGESTED_MEMORY;
        } catch (NumberFormatException e) {
            return GameSettings.SUGGESTED_MEMORY;
        }
    }

    private static void saveMemory(int memoryMb) {
        writeProps(props -> props.setProperty(KEY_MEMORY, Integer.toString(memoryMb)));
    }
}

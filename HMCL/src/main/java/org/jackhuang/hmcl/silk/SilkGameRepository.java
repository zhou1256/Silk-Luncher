package org.jackhuang.hmcl.silk;

import org.jackhuang.hmcl.game.DefaultGameRepository;

import java.nio.file.Path;

/**
 * 启用版本隔离的游戏仓库：每个版本的运行目录指向 {@code versions/<id>/}，
 * 使 mods / saves / config 等按版本独立存放，避免不同版本的模组互相冲突。
 */
public final class SilkGameRepository extends DefaultGameRepository {

    public SilkGameRepository(Path baseDirectory) {
        super(baseDirectory);
    }

    @Override
    public Path getRunDirectory(String id) {
        return getVersionRoot(id);
    }
}

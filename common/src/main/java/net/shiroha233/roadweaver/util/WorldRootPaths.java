/* 文件职责：按 ServerLevel 缓存世界根目录路径，隔离原版非线程安全的路径解析。 */
package net.shiroha233.roadweaver.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldRootPaths {
    private static final ConcurrentHashMap<ServerLevel, Path> ROOTS = new ConcurrentHashMap<>();

    private WorldRootPaths() {}

    public static Path root(ServerLevel level) {
        return ROOTS.computeIfAbsent(level, l -> l.getServer().getWorldPath(LevelResource.ROOT));
    }

    public static void clear(ServerLevel level) {
        if (level != null) ROOTS.remove(level);
    }

    public static void clearAll() {
        ROOTS.clear();
    }
}

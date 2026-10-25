package net.shiroha233.roadweaver.persistence.files;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.util.WorldRootPaths;

import java.nio.file.Path;

/**
 * 文件型持久化的统一路径解析。
 */
public final class FileStoragePathResolver {
    private FileStoragePathResolver() {}

    private static final String ROOT_DIR = "data/roadweaver/persistence";

    public static Path root(ServerLevel level) {
        return WorldRootPaths.root(level).resolve(ROOT_DIR);
    }

    public static Path categoryRoot(ServerLevel level, String category) {
        return root(level).resolve(category).resolve(dimensionKey(level.dimension().location()));
    }

    public static String dimensionKey(ResourceLocation location) {
        if (location == null) return "unknown";
        return location.getNamespace() + "_" + location.getPath().replace('/', '_');
    }
}
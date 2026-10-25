package net.shiroha233.roadweaver.persistence.chunk;

import net.minecraft.world.level.ChunkPos;

import java.util.Objects;

/**
 * 区块坐标值对象。文件名和内存索引都使用同一套坐标编码，避免把负坐标当作字符串排序或位移处理。
 */
public record RoadChunkKey(int x, int z) {
    public static RoadChunkKey from(ChunkPos pos) {
        Objects.requireNonNull(pos, "pos");
        return new RoadChunkKey(pos.x, pos.z);
    }

    public static RoadChunkKey fromBlock(int blockX, int blockZ) {
        return new RoadChunkKey(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    public static RoadChunkKey fromPacked(long packed) {
        return new RoadChunkKey((int) (packed >> 32), (int) packed);
    }

    public long packed() {
        return pack(x, z);
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    public String fileName() {
        return x + "_" + z + ".json";
    }

    public static boolean isValidChunkFileName(String fileName) {
        return parseFileName(fileName) != null;
    }

    public static RoadChunkKey parseFileName(String fileName) {
        if (fileName == null || !fileName.endsWith(".json")) return null;
        String stem = fileName.substring(0, fileName.length() - ".json".length());
        int separator = stem.indexOf('_');
        if (separator <= 0 || separator >= stem.length() - 1 || separator != stem.lastIndexOf('_')) return null;
        try {
            return new RoadChunkKey(
                    Integer.parseInt(stem.substring(0, separator)),
                    Integer.parseInt(stem.substring(separator + 1)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

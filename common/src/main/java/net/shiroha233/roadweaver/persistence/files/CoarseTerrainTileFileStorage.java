/* 文件职责：持久化粗采样地形瓦片，并保留既有瓦片路径读取能力。 */
package net.shiroha233.roadweaver.persistence.files;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTile;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * 粗采样地形瓦片文件存储。
 */
public final class CoarseTerrainTileFileStorage {
    private CoarseTerrainTileFileStorage() {}

    private static final int PAYLOAD_MAGIC = 0x52575432;
    private static final String CATEGORY = "terrain";
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    public static CoarseTerrainTile loadTile(ServerLevel level, CoarseTerrainTileKey key) {
        if (level == null || key == null) return null;
        CoarseTerrainTile tile = readFile(level, key);
        if (tile != null) return tile;
        return null;
    }

    public static void saveTile(ServerLevel level, CoarseTerrainTile tile) {
        if (level == null || tile == null) return;
        try {
            byte[] data = encode(tile);
            FileStorageIO.writeBytesAtomic(tilePath(level, tile.key()), data);
        } catch (IOException e) {
            throw new IllegalStateException("failed to save terrain tile", e);
        }
    }

    public static void deleteBySchemaVersion(ServerLevel level, int currentSchemaVersion) {
        if (level == null) return;
        FileStorageIO.deleteTree(FileStoragePathResolver.categoryRoot(level, CATEGORY), null, "清理旧版粗采样地形文件失败");
    }

    private static CoarseTerrainTile readFile(ServerLevel level, CoarseTerrainTileKey key) {
        Path file = tilePath(level, key);
        if (!Files.exists(file)) {
            file = legacyTilePath(level, key);
            if (!Files.exists(file)) return null;
        }
        try {
            byte[] data = Files.readAllBytes(file);
            CoarseTerrainTile tile = decode(key, data);
            if (tile == null) {
                FileStorageIO.quarantineCorrupt(file, LOGGER, "粗采样地形瓦片损坏，已隔离");
            }
            return tile;
        } catch (IOException e) {
            return null;
        }
    }

    private static Path tilePath(ServerLevel level, CoarseTerrainTileKey key) {
        return FileStoragePathResolver.categoryRoot(level, CATEGORY)
                .resolve(Integer.toString(key.schemaVersion()))
                .resolve(Integer.toString(key.tileSizeChunks()))
                .resolve(Integer.toString(key.step()))
                .resolve(Integer.toString(key.tileX()))
                .resolve(key.tileZ() + ".bin");
    }

    private static Path legacyTilePath(ServerLevel level, CoarseTerrainTileKey key) {
        return FileStoragePathResolver.categoryRoot(level, CATEGORY)
                .resolve(Integer.toString(key.tileSizeChunks()))
                .resolve(Integer.toString(key.step()))
                .resolve(Integer.toString(key.tileX()))
                .resolve(key.tileZ() + ".bin");
    }

    private static byte[] encode(CoarseTerrainTile tile) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(tile.sampleCount() * 9 + 32);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(raw))) {
            out.writeInt(PAYLOAD_MAGIC);
            out.writeInt(tile.sampleWidth());
            out.writeInt(tile.sampleHeight());
            out.writeInt(tile.seaLevel());
            for (short value : tile.heights()) out.writeShort(value);
            for (short value : tile.oceanFloors()) out.writeShort(value);
            out.write(tile.flags());
            for (int value : tile.terrainArgb()) out.writeInt(value);
        }
        return raw.toByteArray();
    }

    private static CoarseTerrainTile decode(CoarseTerrainTileKey key, byte[] data) {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            int magic = in.readInt();
            if (magic != PAYLOAD_MAGIC) return null;
            int sampleWidth = in.readInt();
            int sampleHeight = in.readInt();
            int seaLevel = in.readInt();
            if (sampleWidth != key.sampleWidth() || sampleHeight != key.sampleHeight()) return null;
            int expected = Math.multiplyExact(sampleWidth, sampleHeight);
            if (expected <= 0) return null;
            short[] heights = new short[expected];
            short[] oceanFloors = new short[expected];
            byte[] flags = new byte[expected];
            int[] terrainArgb = new int[expected];
            for (int i = 0; i < expected; i++) heights[i] = in.readShort();
            for (int i = 0; i < expected; i++) oceanFloors[i] = in.readShort();
            in.readFully(flags);
            for (int i = 0; i < expected; i++) terrainArgb[i] = in.readInt();
            return new CoarseTerrainTile(key, seaLevel, sampleWidth, sampleHeight, heights, oceanFloors, flags, terrainArgb);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

}

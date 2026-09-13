package net.shiroha233.roadweaver.client.map.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图数据本地存储管理
 */
public final class MapDataStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_DIR = "config/roadweaver/mapdata";
    private static final String NOTES_FILE = "notes.json";

    private MapDataStorage() {}

    public static class NotesData {
        public Map<String, String> aliases = new HashMap<>();
        public Map<String, List<String>> notes = new HashMap<>();
    }

    public record DimensionPos(ResourceLocation dimension, BlockPos pos) {}

    private static Path getDataRoot() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(DATA_DIR);
    }

    public static Path getWorldDataDir() {
        String worldId = getWorldId();
        if (worldId == null) return null;
        
        String safeName = worldId.replaceAll("[<>:\"/\\\\|?*]", "_");
        return getDataRoot().resolve(safeName);
    }

    public static String getWorldId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;

        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().ip;
        }
        return null;
    }

    public static NotesData loadNotes() {
        Path dir = getWorldDataDir();
        if (dir == null) return new NotesData();

        Path file = dir.resolve(NOTES_FILE);
        if (!Files.exists(file)) return new NotesData();

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            NotesData data = GSON.fromJson(json, NotesData.class);
            LOGGER.debug("[RoadWeaver] 已加载地图笔记: {}", file);
            return data != null ? data : new NotesData();
        } catch (IOException e) {
            LOGGER.error("[RoadWeaver] 加载地图笔记失败: {}", file, e);
            return new NotesData();
        }
    }

    public static void saveNotes(NotesData data) {
        Path dir = getWorldDataDir();
        if (dir == null) return;

        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(NOTES_FILE);
            String json = GSON.toJson(data);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            LOGGER.debug("[RoadWeaver] 已保存地图笔记: {}", file);
        } catch (IOException e) {
            LOGGER.error("[RoadWeaver] 保存地图笔记失败", e);
        }
    }

    public static String posToKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static BlockPos keyToPos(String key) {
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String dimensionPosToKey(ResourceLocation dimension, BlockPos pos) {
        if (dimension == null || pos == null) return null;
        return dimension + "|" + posToKey(pos);
    }

    public static DimensionPos keyToDimensionPos(String key) {
        if (key == null) return null;
        int separator = key.indexOf('|');
        if (separator <= 0 || separator >= key.length() - 1) return null;
        try {
            ResourceLocation dimension = new ResourceLocation(key.substring(0, separator));
            BlockPos pos = keyToPos(key.substring(separator + 1));
            return pos == null ? null : new DimensionPos(dimension, pos);
        } catch (RuntimeException invalidKey) {
            return null;
        }
    }
}

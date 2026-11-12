package net.shiroha233.roadweaver.client.map.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.map.search.MapSearchResult;
import net.shiroha233.roadweaver.map.search.MapStructureSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 客户端地图笔记存储 - 按存档隔离并持久化
 */
public final class ClientMapNotes {
    private ClientMapNotes() {}

    private static String currentWorldId = null;

    private static final Map<MapDataStorage.DimensionPos, String> aliases = new HashMap<>();
    private static final Map<MapDataStorage.DimensionPos, List<String>> notes = new HashMap<>();

    private static boolean dirty = false;

    public static void onWorldJoin() {
        String worldId = MapDataStorage.getWorldId();
        if (worldId == null) return;

        if (worldId.equals(currentWorldId)) return;

        if (currentWorldId != null && dirty) {
            saveToFile();
        }

        currentWorldId = worldId;
        aliases.clear();
        notes.clear();
        dirty = false;

        MapDataStorage.NotesData data = MapDataStorage.loadNotes();
        for (Map.Entry<String, String> e : data.aliases.entrySet()) {
            MapDataStorage.DimensionPos key = MapDataStorage.keyToDimensionPos(e.getKey());
            if (key != null) aliases.put(key, e.getValue());
        }
        for (Map.Entry<String, List<String>> e : data.notes.entrySet()) {
            MapDataStorage.DimensionPos key = MapDataStorage.keyToDimensionPos(e.getKey());
            if (key != null) notes.put(key, new ArrayList<>(e.getValue()));
        }
    }

    public static void onWorldLeave() {
        if (dirty) {
            saveToFile();
        }
        currentWorldId = null;
        aliases.clear();
        notes.clear();
        dirty = false;
    }

    public static void saveToFile() {
        MapDataStorage.NotesData data = new MapDataStorage.NotesData();
        for (Map.Entry<MapDataStorage.DimensionPos, String> e : aliases.entrySet()) {
            data.aliases.put(MapDataStorage.dimensionPosToKey(e.getKey().dimension(), e.getKey().pos()), e.getValue());
        }
        for (Map.Entry<MapDataStorage.DimensionPos, List<String>> e : notes.entrySet()) {
            data.notes.put(MapDataStorage.dimensionPosToKey(e.getKey().dimension(), e.getKey().pos()), e.getValue());
        }
        MapDataStorage.saveNotes(data);
        dirty = false;
    }

    @Nullable
    public static String getAlias(BlockPos pos) {
        MapDataStorage.DimensionPos key = currentKey(pos);
        return key == null ? null : aliases.get(key);
    }

    public static void setAlias(BlockPos pos, String alias) {
        MapDataStorage.DimensionPos key = currentKey(pos);
        if (key == null) return;
        if (alias == null || alias.isBlank()) {
            if (aliases.remove(key) != null) {
                dirty = true;
                saveToFile();
            }
        } else {
            aliases.put(key, alias);
            dirty = true;
            saveToFile();
        }
    }

    public static List<MapSearchResult> searchAliases(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) return List.of();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        ArrayList<MapSearchResult> results = new ArrayList<>();
        ResourceLocation dimension = currentDimension();
        for (Map.Entry<MapDataStorage.DimensionPos, String> entry : aliases.entrySet()) {
            if (dimension == null || !dimension.equals(entry.getKey().dimension())) continue;
            String alias = entry.getValue();
            if (alias == null || !alias.toLowerCase(Locale.ROOT).contains(normalized)) continue;
            results.add(new MapSearchResult(entry.getKey().pos(), alias, MapStructureSource.UNKNOWN.id()));
            if (results.size() >= limit) break;
        }
        return List.copyOf(results);
    }

    public static List<String> getNotes(BlockPos pos) {
        MapDataStorage.DimensionPos key = currentKey(pos);
        return key == null ? List.of() : notes.getOrDefault(key, List.of());
    }

    public static void addNote(BlockPos pos, String note) {
        if (note == null || note.isBlank()) return;
        MapDataStorage.DimensionPos key = currentKey(pos);
        if (key == null) return;
        notes.computeIfAbsent(key, k -> new ArrayList<>()).add(note);
        dirty = true;
        saveToFile();
    }

    public static void clearNotes(BlockPos pos) {
        MapDataStorage.DimensionPos key = currentKey(pos);
        if (key != null && notes.remove(key) != null) {
            dirty = true;
            saveToFile();
        }
    }

    private static MapDataStorage.DimensionPos currentKey(BlockPos pos) {
        ResourceLocation dimension = currentDimension();
        return dimension == null || pos == null ? null : new MapDataStorage.DimensionPos(dimension, pos);
    }

    private static ResourceLocation currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.level == null ? null : minecraft.level.dimension().location();
    }
}

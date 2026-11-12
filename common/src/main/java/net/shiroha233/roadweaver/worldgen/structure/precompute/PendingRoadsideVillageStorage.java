package net.shiroha233.roadweaver.worldgen.structure.precompute;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路边村庄待注入存储
 */
public final class PendingRoadsideVillageStorage {
    private PendingRoadsideVillageStorage() {}

    private static final int MAX_INJECTED = 2048;
    private static final Map<Long, List<PendingRoadsideVillage>> PENDING = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> PLACED = createPlacementSet();
    private static final Set<Long> INJECTED_CHUNKS = createLimitedChunkSet();

    private static Set<ResourceLocation> createPlacementSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    private static Set<Long> createLimitedChunkSet() {
        return Collections.newSetFromMap(
            Collections.synchronizedMap(
                new java.util.LinkedHashMap<Long, Boolean>(256, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(java.util.Map.Entry<Long, Boolean> eldest) {
                        return size() > MAX_INJECTED;
                    }
                }
            )
        );
    }

    public static boolean addPendingVillage(ServerLevel level, PendingRoadsideVillage village) {
        if (!isOverworld(level) || village == null || village.placementId() == null) return false;
        if (!PLACED.add(village.placementId())) {
            return false;
        }

        long chunkKey = village.chunkKey();
        PENDING.computeIfAbsent(chunkKey, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(village);
        return true;
    }

    public static List<PendingRoadsideVillage> getPendingVillages(ServerLevel level, ChunkPos chunkPos) {
        if (!isOverworld(level) || chunkPos == null) return Collections.emptyList();
        long chunkKey = chunkPos.toLong();

        if (INJECTED_CHUNKS.contains(chunkKey)) {
            return Collections.emptyList();
        }

        List<PendingRoadsideVillage> villages = PENDING.get(chunkKey);
        return villages == null ? Collections.emptyList() : new ArrayList<>(villages);
    }

    public static void markAsInjected(ServerLevel level, ChunkPos chunkPos) {
        if (!isOverworld(level) || chunkPos == null) return;
        long chunkKey = chunkPos.toLong();
        INJECTED_CHUNKS.add(chunkKey);
        PENDING.remove(chunkKey);
    }

    public static void clearAll() {
        PENDING.clear();
        PLACED.clear();
        INJECTED_CHUNKS.clear();
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }
}

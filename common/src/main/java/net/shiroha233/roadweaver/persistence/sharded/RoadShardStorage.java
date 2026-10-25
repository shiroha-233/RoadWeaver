package net.shiroha233.roadweaver.persistence.sharded;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.persistence.RoadReplacement;
import net.shiroha233.roadweaver.persistence.chunk.RoadFootprint;
import net.shiroha233.roadweaver.persistence.files.RoadFileStorage;
import net.shiroha233.roadweaver.worldgen.road.RoadWorldgenPlanCache;

import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** 道路数据存储门面。 */
public final class RoadShardStorage {
    private RoadShardStorage() {}

    public static void addRoad(ServerLevel level, RoadData road) {
        if (!isOverworld(level) || road == null || road.roadSegmentList() == null
                || road.roadSegmentList().isEmpty()) return;
        long fingerprint = RoadFileStorage.computeFingerprint(road);
        RoadData previous = RoadFileStorage.loadByFingerprint(level, fingerprint);
        RoadFileStorage.addRoad(level, road);
        LinkedHashSet<Long> affected = new LinkedHashSet<>(affectedChunks(previous));
        affected.addAll(affectedChunks(road));
        RoadWorldgenPlanCache.invalidate(level, affected);
    }

    public static void preload(ServerLevel level) {
        if (isOverworld(level)) RoadFileStorage.preload(level);
    }

    public static List<RoadData> queryRect(ServerLevel level,
                                           int minBlockX, int minBlockZ,
                                           int maxBlockX, int maxBlockZ) {
        return isOverworld(level)
                ? RoadFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)
                : List.of();
    }

    public static List<RoadData> queryChunk(ServerLevel level, ChunkPos chunkPos) {
        return isOverworld(level) ? RoadFileStorage.queryChunk(level, chunkPos) : List.of();
    }

    public static List<RoadData> queryChunk(ServerLevel level, int chunkX, int chunkZ) {
        return isOverworld(level) ? RoadFileStorage.queryChunk(level, chunkX, chunkZ) : List.of();
    }

    public static CompletableFuture<List<RoadData>> queryRectAsync(ServerLevel level,
                                                                    int minBlockX, int minBlockZ,
                                                                    int maxBlockX, int maxBlockZ) {
        if (!isOverworld(level)) return CompletableFuture.completedFuture(List.of());
        return RoadFileStorage.queryRectAsync(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    public static List<RoadData> loadAll(ServerLevel level) {
        return isOverworld(level) ? RoadFileStorage.loadAll(level) : List.of();
    }

    public static RoadData loadByFingerprint(ServerLevel level, long fingerprint) {
        return isOverworld(level) ? RoadFileStorage.loadByFingerprint(level, fingerprint) : null;
    }

    public static void deleteRoad(ServerLevel level, long fingerprint) {
        if (!isOverworld(level)) return;
        RoadData previous = RoadFileStorage.loadByFingerprint(level, fingerprint);
        RoadFileStorage.deleteRoad(level, fingerprint);
        RoadWorldgenPlanCache.invalidate(level, affectedChunks(previous));
    }

    public static void replaceRoad(ServerLevel level, long oldFingerprint, RoadData newRoad) {
        replaceRoads(level, List.of(new RoadReplacement(oldFingerprint, newRoad)));
    }

    public static void replaceRoads(ServerLevel level, Collection<RoadReplacement> replacements) {
        if (!isOverworld(level) || replacements == null || replacements.isEmpty()) return;
        LinkedHashSet<Long> affected = new LinkedHashSet<>();
        for (RoadReplacement replacement : replacements) {
            if (replacement == null) continue;
            RoadData previous = RoadFileStorage.loadByFingerprint(level, replacement.oldFingerprint());
            affected.addAll(affectedChunks(previous));
            affected.addAll(affectedChunks(replacement.newRoad()));
        }
        RoadFileStorage.replaceRoads(level, replacements);
        RoadWorldgenPlanCache.invalidate(level, affected);
    }

    public static long computeFingerprint(RoadData road) {
        return RoadFileStorage.computeFingerprint(road);
    }

    public static boolean hasAnyRoad(ServerLevel level) {
        return isOverworld(level) && RoadFileStorage.hasAnyRoad(level);
    }

    public static boolean hasRoadInRect(ServerLevel level,
                                        int minBlockX, int minBlockZ,
                                        int maxBlockX, int maxBlockZ) {
        return isOverworld(level)
                && RoadFileStorage.hasRoadInRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    public static void flushAll(ServerLevel level) {
        if (isOverworld(level)) RoadFileStorage.flush(level);
    }

    public static void clearAll(ServerLevel level) {
        if (!isOverworld(level)) return;
        RoadFileStorage.clearAll(level);
        RoadWorldgenPlanCache.invalidate(level);
    }

    public static void shutdown() {
        RoadFileStorage.shutdown();
        RoadWorldgenPlanCache.clearAll();
    }

    public static void closeConnection(ServerLevel level) {
        if (!isOverworld(level)) return;
        RoadWorldgenPlanCache.clear(level);
        RoadFileStorage.close(level);
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }

    private static Set<Long> affectedChunks(RoadData road) {
        return road == null ? Set.of() : RoadFootprint.from(road).chunkKeys();
    }
}

package net.shiroha233.roadweaver.map.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.StructureFileStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构点快照收集。
 */
public final class MapStructureCollector {
    private MapStructureCollector() {}

    public record Result(List<BlockPos> structures, List<StructureInfo> infos, Map<BlockPos, Integer> sources) {}

    public static Result collect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        ModConfig cfg = ConfigService.get();
        boolean allowPredicted = cfg != null
                && cfg.structurePrediction().enabled()
                && Level.OVERWORLD.equals(level.dimension());
        int[] sources = allowPredicted
                ? new int[]{StructureFileStorage.SOURCE_MANUAL, StructureFileStorage.SOURCE_PREDICTED}
                : new int[]{StructureFileStorage.SOURCE_MANUAL};

        StructureFileStorage.StructureSnapshot storageSnapshot = StructureFileStorage.getStructureSnapshot(level);
        List<StructureInfo> cached = new ArrayList<>();
        HashMap<Long, StructureInfo> bestInfoByPos = new HashMap<>();
        HashMap<Long, StructureInfo> identityByPos = new HashMap<>();
        HashSet<BlockPos> structuresSet = new HashSet<>();
        LinkedHashMap<BlockPos, Integer> sourcesByPos = new LinkedHashMap<>();
        for (StructureInfo info : storageSnapshot.locations().structureInfos()) {
            if (!inside(info, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) continue;
            mergeBestStructureInfo(identityByPos, info);
            if (containsSource(sources, storageSnapshot.sourceAt(info.pos()))) cached.add(info);
        }
        for (StructureInfo info : cached) {
            if (info == null || info.pos() == null) continue;
            mergeBestStructureInfo(bestInfoByPos, info);
            BlockPos p = info.pos();
            BlockPos normalized = new BlockPos(p.getX(), 0, p.getZ());
            structuresSet.add(normalized);
            sourcesByPos.put(normalized, storageSnapshot.sourceAt(p));
        }
        for (StructureConnection connection : MapConnectionCollector.collect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
            addConnectionEndpoint(bestInfoByPos, identityByPos, structuresSet, connection.from(), minBlockX, minBlockZ, maxBlockX, maxBlockZ);
            addConnectionEndpoint(bestInfoByPos, identityByPos, structuresSet, connection.to(), minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        }
        return new Result(new ArrayList<>(structuresSet), new ArrayList<>(bestInfoByPos.values()), sourcesByPos);
    }

    private static void addConnectionEndpoint(java.util.Map<Long, StructureInfo> infos,
                                               java.util.Map<Long, StructureInfo> identities,
                                               HashSet<BlockPos> structures,
                                               BlockPos pos,
                                              int minBlockX,
                                              int minBlockZ,
                                              int maxBlockX,
                                              int maxBlockZ) {
        if (pos == null) return;
        int x = pos.getX();
        int z = pos.getZ();
        if (x < minBlockX || x > maxBlockX || z < minBlockZ || z > maxBlockZ) return;
        BlockPos normalized = new BlockPos(x, 0, z);
        structures.add(normalized);
        long key = (((long) x) << 32) ^ (z & 0xffffffffL);
        StructureInfo identity = identities.get(key);
        mergeBestStructureInfo(infos, identity != null ? identity : new StructureInfo(normalized, "unknown"));
    }

    private static void mergeBestStructureInfo(java.util.Map<Long, StructureInfo> out, StructureInfo info) {
        if (out == null || info == null || info.pos() == null) return;
        BlockPos p = info.pos();
        int x = p.getX();
        int z = p.getZ();
        long key = (((long) x) << 32) ^ (z & 0xffffffffL);
        StructureInfo prev = out.get(key);
        if (prev == null) {
            out.put(key, new StructureInfo(new BlockPos(x, 0, z), info.structureId()));
            return;
        }
        String prevId = prev.structureId();
        String nextId = info.structureId();
        if (!StructureInfo.isKnownId(prevId) && StructureInfo.isKnownId(nextId)) {
            out.put(key, new StructureInfo(new BlockPos(x, 0, z), nextId));
        }
    }

    private static boolean inside(StructureInfo info, int minX, int minZ, int maxX, int maxZ) {
        if (info == null || info.pos() == null) return false;
        int x = info.pos().getX();
        int z = info.pos().getZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private static boolean containsSource(int[] sources, int source) {
        if (sources == null || sources.length == 0) return true;
        for (int candidate : sources) {
            if (candidate == source) return true;
        }
        return false;
    }
}

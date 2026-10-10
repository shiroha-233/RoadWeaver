package net.shiroha233.roadweaver.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.generation.ConnectionListUpdater;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.map.MapPatchService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.planning.RoadPlanningService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RoadNetworkApi {
    private RoadNetworkApi() {
    }

    public static void registerStructureEndpoint(ServerLevel level, BlockPos pos) {
        registerStructureEndpoint(level, pos, null, false);
    }

    public static void registerStructureEndpoint(ServerLevel level, BlockPos pos, boolean autoConnect) {
        registerStructureEndpoint(level, pos, null, autoConnect);
    }

    public static void registerStructureEndpoint(ServerLevel level, BlockPos pos, String structureId,
            boolean autoConnect) {
        if (level == null || pos == null || !Level.OVERWORLD.equals(level.dimension()))
            return;
        WorldDataProvider provider = WorldDataProvider.getInstance();
        StructureLocationData existing = provider.getStructureLocations(level);
        List<BlockPos> locations = existing != null ? new ArrayList<>(existing.structureLocations())
                : new ArrayList<>();
        List<StructureInfo> infos = existing != null ? new ArrayList<>(existing.structureInfos())
                : new ArrayList<>();

        if (structureId != null && !structureId.isEmpty()) {
            StructureInfo info = new StructureInfo(pos, structureId);
            infos.add(info);
            if (!locations.contains(pos)) {
                locations.add(pos);
            }
        } else {
            if (!locations.contains(pos)) {
                locations.add(pos);
            }
        }

        StructureLocationData updated = new StructureLocationData(locations, infos);
        provider.setStructureLocations(level, updated);

        String id = (structureId != null && !structureId.isEmpty()) ? structureId : "unknown";
        StructureFileStorage.addStructures(
                level,
                java.util.List.of(new StructureInfo(new BlockPos(pos.getX(), 0, pos.getZ()), id)),
                StructureFileStorage.SOURCE_MANUAL
        );
    }

    public static void ensureConnection(ServerLevel level, BlockPos from, BlockPos to) {
        ensureConnection(level, from, to, false);
    }

    public static void ensureConnection(ServerLevel level, BlockPos from, BlockPos to, boolean generateImmediately) {
        if (level == null || from == null || to == null || !Level.OVERWORLD.equals(level.dimension()))
            return;
        if (from.equals(to))
            return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        StructureLocationData existing = provider.getStructureLocations(level);
        List<BlockPos> locations = existing != null ? new ArrayList<>(existing.structureLocations())
                : new ArrayList<>();
        List<StructureInfo> infos = existing != null ? new ArrayList<>(existing.structureInfos())
                : new ArrayList<>();

        boolean changed = false;
        if (!locations.contains(from)) {
            locations.add(from);
            changed = true;
        }
        if (!locations.contains(to)) {
            locations.add(to);
            changed = true;
        }
        if (changed) {
            StructureLocationData updated = new StructureLocationData(locations, infos);
            provider.setStructureLocations(level, updated);
        }

        StructureFileStorage.addStructures(
                level,
                java.util.List.of(
                        new StructureInfo(new BlockPos(from.getX(), 0, from.getZ()), "unknown"),
                        new StructureInfo(new BlockPos(to.getX(), 0, to.getZ()), "unknown")
                ),
                StructureFileStorage.SOURCE_MANUAL
        );

        StructureConnection created = ConnectionListUpdater.update(level, all -> {
            for (StructureConnection c : all) {
                if (sameEdge(c, from, to)) return Optional.empty();
            }
            StructureConnection connection = new StructureConnection(from, to, ConnectionStatus.PLANNED);
            all.add(connection);
            return Optional.of(connection);
        });
        if (created != null) {
            MapPatchService.publishConnection(level, created);
        }

        if (generateImmediately) {
            StructureConnection conn = new StructureConnection(from, to,
                    ConnectionStatus.GENERATING);
            ConnectionListUpdater.update(level, all -> {
                replaceEdge(all, from, to, conn);
                return Optional.of(Boolean.TRUE);
            });
            boolean success = RoadGenerationService.generateTask(level, conn);
            ConnectionStatus finalStatus = success ? ConnectionStatus.COMPLETED
                    : ConnectionStatus.FAILED;
            StructureConnection finalConn = new StructureConnection(from, to, finalStatus);

            ConnectionListUpdater.update(level, all -> {
                replaceEdge(all, from, to, finalConn);
                return Optional.of(Boolean.TRUE);
            });
        }
    }

    /** 就地替换同一条边，不存在时追加。与逐条重建列表的旧写法等价。 */
    private static void replaceEdge(List<StructureConnection> all, BlockPos from, BlockPos to,
                                    StructureConnection replacement) {
        for (int i = 0; i < all.size(); i++) {
            if (sameEdge(all.get(i), from, to)) {
                all.set(i, replacement);
                return;
            }
        }
        all.add(replacement);
    }

    public static void planRegion(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension()))
            return;
        RoadPlanningService.planRectAsync(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    private static boolean sameEdge(StructureConnection c, BlockPos a, BlockPos b) {
        BlockPos af = c.from();
        BlockPos at = c.to();
        return (af.equals(a) && at.equals(b)) || (af.equals(b) && at.equals(a));
    }
}

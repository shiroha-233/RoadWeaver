package net.shiroha233.roadweaver.persistence;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨平台世界数据访问抽象，由 Fabric/Forge 平台端提供实现
 */
public abstract class WorldDataProvider {

    @ExpectPlatform
    public static WorldDataProvider getInstance() {
        throw new AssertionError();
    }

    public abstract StructureLocationData getStructureLocations(ServerLevel level);
    public abstract void setStructureLocations(ServerLevel level, StructureLocationData data);

    public abstract List<StructureConnection> getStructureConnections(ServerLevel level);
    public abstract void setStructureConnections(ServerLevel level, List<StructureConnection> connections);

    public abstract Set<Long> getPlannedTileKeys(ServerLevel level);
    public abstract void setPlannedTileKeys(ServerLevel level, Set<Long> keys);
    public abstract Map<Long, Long> getPlannedTileCenters(ServerLevel level);
    public abstract void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers);

    /** 放在公共层以免平台模块重复实现：两个平台的 provider 共用同一后端。 */
    public boolean isPlannedTile(ServerLevel level, long tileKey) {
        return StructureFileStorage.isPlannedTile(level, tileKey);
    }

    public void markPlannedTile(ServerLevel level, long tileKey, int centerChunkX, int centerChunkZ) {
        StructureFileStorage.markPlannedTile(level, tileKey, centerChunkX, centerChunkZ);
    }

    public void prunePlannedTilesIfTooLarge(ServerLevel level, int maxKeys) {
        StructureFileStorage.prunePlannedTilesIfTooLarge(level, maxKeys);
    }


    /**
     * 添加单个结构位置（幂等）
     */
    public void addStructureLocation(ServerLevel level, BlockPos pos) {
        StructureLocationData data = getStructureLocations(level);
        List<BlockPos> locations = new ArrayList<>(data != null ? data.structureLocations() : new ArrayList<>());
        List<StructureInfo> infos = new ArrayList<>(data != null ? data.structureInfos() : new ArrayList<>());
        if (!locations.contains(pos)) {
            locations.add(pos);
            setStructureLocations(level, new StructureLocationData(locations, infos));
        }
    }
}
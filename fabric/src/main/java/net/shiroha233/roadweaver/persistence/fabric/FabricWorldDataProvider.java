package net.shiroha233.roadweaver.persistence.fabric;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.StructureFileStorage;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fabric 平台世界数据提供者实现
 */
public class FabricWorldDataProvider extends WorldDataProvider {

    @Override
    public StructureLocationData getStructureLocations(ServerLevel level) {
        StructureLocationData current = StructureFileStorage.getStructureLocations(level);
        if (hasStructureLocations(current)) return current;
        StructureLocationData legacy = ((AttachmentTarget) level).getAttached(WorldDataAttachment.STRUCTURE_LOCATIONS);
        if (hasStructureLocations(legacy)) {
            StructureFileStorage.setStructureLocations(level, legacy);
            return StructureFileStorage.getStructureLocations(level);
        }
        return current;
    }

    @Override
    public void setStructureLocations(ServerLevel level, StructureLocationData data) {
        StructureFileStorage.setStructureLocations(level, data);
    }

    @Override
    public List<StructureConnection> getStructureConnections(ServerLevel level) {
        List<StructureConnection> current = StructureFileStorage.getStructureConnections(level);
        if (!current.isEmpty()) return current;
        List<StructureConnection> legacy = ((AttachmentTarget) level).getAttached(WorldDataAttachment.CONNECTED_STRUCTURES);
        if (legacy != null && !legacy.isEmpty()) {
            StructureFileStorage.setStructureConnections(level, legacy);
            return StructureFileStorage.getStructureConnections(level);
        }
        return current;
    }

    @Override
    public void setStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        StructureFileStorage.setStructureConnections(level, connections);
    }

    @Override
    public Set<Long> getPlannedTileKeys(ServerLevel level) {
        Set<Long> current = StructureFileStorage.getPlannedTileKeys(level);
        if (!current.isEmpty()) return current;
        Set<Long> legacy = ((AttachmentTarget) level).getAttached(WorldDataAttachment.PLANNED_TILE_KEYS);
        if (legacy != null && !legacy.isEmpty()) {
            StructureFileStorage.setPlannedTileKeys(level, legacy);
            return StructureFileStorage.getPlannedTileKeys(level);
        }
        return current;
    }

    @Override
    public void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        StructureFileStorage.setPlannedTileKeys(level, keys);
    }

    @Override
    public Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        Map<Long, Long> current = StructureFileStorage.getPlannedTileCenters(level);
        if (!current.isEmpty()) return current;
        Map<Long, Long> legacy = ((AttachmentTarget) level).getAttached(WorldDataAttachment.PLANNED_TILE_CENTERS);
        if (legacy != null && !legacy.isEmpty()) {
            StructureFileStorage.setPlannedTileCenters(level, legacy);
            return StructureFileStorage.getPlannedTileCenters(level);
        }
        return current;
    }

    @Override
    public void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        StructureFileStorage.setPlannedTileCenters(level, centers);
    }

    private static boolean hasStructureLocations(StructureLocationData data) {
        return data != null
                && ((!data.structureLocations().isEmpty()) || (!data.structureInfos().isEmpty()));
    }

    }

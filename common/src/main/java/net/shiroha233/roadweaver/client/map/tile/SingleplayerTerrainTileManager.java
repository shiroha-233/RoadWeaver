package net.shiroha233.roadweaver.client.map.tile;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.planning.pipeline.TerrainSamplingSessionSnapshot;
import net.shiroha233.roadweaver.planning.pipeline.TerrainSamplingSessions;

import java.util.Objects;

/**
 * 单人模式 terrain 瓦片只读调度。
 */
public final class SingleplayerTerrainTileManager extends AbstractSingleplayerTileManager {
    private volatile MapTileLayer terrainLayer = MapTileLayer.TERRAIN_COARSE;

    public void selectTerrainLayer(MapTileLayer layer) {
        MapTileLayer nextLayer = Objects.requireNonNull(layer, "layer");
        if (!nextLayer.isTerrainLayer()) {
            throw new IllegalArgumentException("terrain layer required: " + nextLayer);
        }
        if (terrainLayer != nextLayer) {
            terrainLayer = nextLayer;
            clear();
        }
    }

    public TerrainSamplingSessionSnapshot syncSamplingLayer(ServerLevel level) {
        if (level == null) {
            return null;
        }
        TerrainSamplingSessionSnapshot session = TerrainSamplingSessions.forLevel(level).snapshot();
        selectTerrainLayer(session.effectiveMode() == TerrainSamplingMode.FULL_REGION
                ? MapTileLayer.TERRAIN_ACCURATE
                : MapTileLayer.TERRAIN_COARSE);
        return session;
    }

    public MapTileLayer selectedTerrainLayer() {
        return terrainLayer;
    }

    @Override
    protected MapTileLayer layer() {
        return terrainLayer;
    }
}

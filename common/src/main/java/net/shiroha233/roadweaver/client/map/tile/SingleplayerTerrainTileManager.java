package net.shiroha233.roadweaver.client.map.tile;

import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;

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

    public MapTileLayer selectedTerrainLayer() {
        return terrainLayer;
    }

    @Override
    protected MapTileLayer layer() {
        return terrainLayer;
    }
}

package net.shiroha233.roadweaver.map.tile.core;

/**
 * 地图瓦片图层类型。
 */
public enum MapTileLayer {
    TERRAIN_COARSE("terrain_coarse"),
    TERRAIN_ACCURATE("terrain_accurate"),
    OVERLAY("overlay");

    private final String folderName;

    MapTileLayer(String folderName) {
        this.folderName = folderName;
    }

    public String folderName() {
        return folderName;
    }

    public boolean isTerrainLayer() {
        return this == TERRAIN_COARSE || this == TERRAIN_ACCURATE;
    }
}

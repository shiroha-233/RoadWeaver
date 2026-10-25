package net.shiroha233.roadweaver.pathfinding.terrain.region;

/**
 * 接收地形 PNG 瓦片写入进度。
 */
@FunctionalInterface
public interface TerrainPngWriteProgress {
    TerrainPngWriteProgress NONE = (completedTiles, totalTiles) -> {};

    void onTileCompleted(long completedTiles, long totalTiles);
}

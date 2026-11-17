package net.shiroha233.roadweaver.map.tile.core;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 由当前玩家位置推导地图 AOI。
 */
public final class MapTileAoiLocator {
    private MapTileAoiLocator() {}

    public static MapTileAoi forPlayer(ServerPlayer player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        Identifier dimensionId = player.level().dimension().identifier();
        int radiusBlocks = dynamicPlanningRadiusBlocks();
        return new MapTileAoi(
                dimensionId,
                player.blockPosition().getX(),
                player.blockPosition().getZ(),
                radiusBlocks);
    }

    public static int dynamicPlanningRadiusBlocks() {
        ModConfig cfg = ConfigService.get();
        int radiusChunks = cfg != null && cfg.planning() != null
                ? Math.max(1, cfg.planning().dynamicPlanRadiusChunks())
                : RoadConstants.DEFAULT_DYNAMIC_PLAN_RADIUS_CHUNKS;
        radiusChunks = Math.min(RoadConstants.COARSE_REGION_MAX_RADIUS_CHUNKS, radiusChunks);
        return radiusChunks * RoadConstants.CHUNK_SIZE_BLOCKS;
    }
}
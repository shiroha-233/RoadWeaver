package net.shiroha233.roadweaver.network.bridge;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.data.MapSnapshotPatch;

/**
 * 服务端地图 patch 推送桥接。
 */
public final class ServerMapPatchBridge {
    private ServerMapPatchBridge() {}

    @ExpectPlatform
    public static void broadcast(ServerLevel level, ResourceLocation dimensionId, MapSnapshotPatch patch) {
        throw new AssertionError();
    }
}
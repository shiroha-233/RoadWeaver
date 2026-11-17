package net.shiroha233.roadweaver.network.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.MapSnapshotPatch;

/**
 * 服务端地图 patch 推送桥接。
 */
public final class ServerMapPatchBridge {
    private ServerMapPatchBridge() {}

    @ExpectPlatform
    public static void broadcast(ServerLevel level, Identifier dimensionId, MapSnapshotPatch patch) {
        throw new AssertionError();
    }
}
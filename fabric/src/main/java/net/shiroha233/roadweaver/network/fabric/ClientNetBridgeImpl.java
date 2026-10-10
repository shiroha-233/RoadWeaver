package net.shiroha233.roadweaver.network.fabric;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;

/**
 * Fabric 平台网络桥接实现
 */
public final class ClientNetBridgeImpl {
    private ClientNetBridgeImpl() {}

    public static void requestSnapshot(int requestSeq,
                                       ResourceLocation dimensionId,
                                       MapLoadPhase phase,
                                       int responseIndex,
                                       int minX,
                                       int minZ,
                                       int maxX,
                                       int maxZ) {
        MapNetworkFabric.requestSnapshot(requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        MapNetworkFabric.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        MapNetworkFabric.requestManualConnect(ax, az, bx, bz);
    }

    public static void requestSearch(int requestSeq, ResourceLocation dimensionId, String query) {
        MapNetworkFabric.requestSearch(requestSeq, dimensionId, query);
    }

    public static void requestRawPathDebug(int requestSeq, ResourceLocation dimensionId) {
        MapNetworkFabric.requestRawPathDebug(requestSeq, dimensionId);
    }
}

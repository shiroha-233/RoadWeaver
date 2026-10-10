package net.shiroha233.roadweaver.network.neoforge;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;

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
        MapNetworkNeoForge.requestSnapshot(requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        MapNetworkNeoForge.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        MapNetworkNeoForge.requestManualConnect(ax, az, bx, bz);
    }

    public static void requestSearch(int requestSeq, ResourceLocation dimensionId, String query) {
        MapNetworkNeoForge.requestSearch(requestSeq, dimensionId, query);
    }

    public static void requestRawPathDebug(int requestSeq, ResourceLocation dimensionId) {
        MapNetworkNeoForge.requestRawPathDebug(requestSeq, dimensionId);
    }
}

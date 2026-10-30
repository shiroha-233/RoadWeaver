package net.shiroha233.roadweaver.network.bridge.neoforge;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.map.data.MapLoadPhase;
import net.shiroha233.roadweaver.network.neoforge.MapNetwork;

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
        MapNetwork.requestSnapshot(requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        MapNetwork.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        MapNetwork.requestManualConnect(ax, az, bx, bz);
    }

    public static void requestSearch(int requestSeq, ResourceLocation dimensionId, String query) {
        MapNetwork.requestSearch(requestSeq, dimensionId, query);
    }

    public static void requestRawPathDebug(int requestSeq, ResourceLocation dimensionId) {
        MapNetwork.requestRawPathDebug(requestSeq, dimensionId);
    }
}

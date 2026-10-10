package net.shiroha233.roadweaver.network;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;

/**
 * 客户端网络桥接
 */
public final class ClientNetBridge {
    private ClientNetBridge() {}

    @ExpectPlatform
    public static void requestSnapshot(int requestSeq,
                                       ResourceLocation dimensionId,
                                       MapLoadPhase phase,
                                       int responseIndex,
                                       int minX,
                                       int minZ,
                                       int maxX,
                                       int maxZ) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void requestTeleport(int x, int y, int z) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void requestSearch(int requestSeq, ResourceLocation dimensionId, String query) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void requestRawPathDebug(int requestSeq, ResourceLocation dimensionId) {
        throw new AssertionError();
    }
}

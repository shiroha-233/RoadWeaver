package net.shiroha233.roadweaver.network;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerLevel;

/**
 * 自动规划采样活动的服务端地图同步端口。
 */
public final class ServerMapPlanningActivityBridge {
    private ServerMapPlanningActivityBridge() {}

    @ExpectPlatform
    public static void broadcast(ServerLevel level) {
        throw new AssertionError();
    }
}

/* 文件职责：注册 Fabric 服务端生命周期与 Tick 钩子。 */
package net.shiroha233.roadweaver.planning.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.generation.IdleRoadGenerationService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.runtime.CacheManager;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.List;

/**
 * Fabric 服务端规划钩子
 */
public final class ServerPlanningHooks {
    private ServerPlanningHooks() {}

    private static int tick;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CacheManager.onServerStarted();
            ThreadPoolManager.onServerStarted(server);
            SignTextService.clearPending();
            IdleRoadGenerationService.onServerStarted();
            
            ServerLevel level = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (level == null) return;

            StructureDiscoveryService.discoverFromLevel(level);

            boolean dedicated = server.isDedicatedServer();
            if (dedicated) {
                RoadGenerationService.onServerStarted();
                if (InitialGenManager.shouldRunInitialGeneration(level)) {
                    RoadPlanningService.initialPlanAsync(level);
                }
                return;
            }
            
            if (InitialGenManager.shouldRunInitialGeneration(level)) {
                RoadGenerationService.onServerStarted();
                InitialGenManager.begin(level);
                InitialGenManager.blockUntilDone(level);
            } else {
                RoadGenerationService.onServerStarted();
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if ((tick++ % 20) == 0) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (Level.OVERWORLD.equals(p.serverLevel().dimension())) {
                        SignTextService.onChunkReady(p.serverLevel(), p.chunkPosition());
                        RoadPlanningService.planAroundPlayer(p);
                        IdleRoadGenerationService.tickPlayer(p);
                    }
                }
            }

            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level != null) {
                IdleRoadGenerationService.tick(level);
                RoadGenerationService.tick(level);
                SignTextService.tick(level);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // 先递增 epoch 并停池，阻止旧任务的完成回调回写世界状态。
            ThreadPoolManager.onServerStopping();
            RoadGenerationService.onServerStopping();
            RoadPlanningService.resetAll();
            CacheManager.onServerStopping(server.getAllLevels());
            SignTextService.clearPending();
        });

        ServerWorldEvents.UNLOAD.register((server, level) -> CacheManager.onDimensionUnload(level));
    }
}

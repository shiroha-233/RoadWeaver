/* 文件职责：注册 NeoForge 服务端生命周期、维度卸载与 Tick 钩子。 */
package net.shiroha233.roadweaver.planning.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.generation.IdleRoadGenerationService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.runtime.CacheManager;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

public final class ServerPlanningHooks {
    private static int tick;

    private ServerPlanningHooks() {
    }

    public static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStopping);
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onLevelUnload);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        tick = 0;
        CacheManager.onServerStarted();
        ThreadPoolManager.onServerStarted(event.getServer());
        SignTextService.clearPending();
        IdleRoadGenerationService.onServerStarted();

        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        StructureDiscoveryService.discoverFromLevel(overworld);
        RoadGenerationService.onServerStarted();

        if (event.getServer().isDedicatedServer()) {
            if (InitialGenManager.shouldRunInitialGeneration(overworld)) {
                RoadPlanningService.initialPlanAsync(overworld);
            }
            return;
        }

        if (InitialGenManager.shouldRunInitialGeneration(overworld)) {
            InitialGenManager.begin(overworld);
            InitialGenManager.blockUntilDone(overworld);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if ((tick++ % 20) == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!Level.OVERWORLD.equals(player.serverLevel().dimension())) {
                    continue;
                }
                SignTextService.onChunkReady(player.serverLevel(), player.chunkPosition());
                RoadPlanningService.planAroundPlayer(player);
                IdleRoadGenerationService.tickPlayer(player);
            }
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            IdleRoadGenerationService.tick(overworld);
            RoadGenerationService.tick(overworld);
            SignTextService.tick(overworld);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        // 先使旧任务的 epoch 失效，再清理状态与持久化缓存。
        ThreadPoolManager.onServerStopping();
        RoadGenerationService.onServerStopping();
        RoadPlanningService.resetAll();
        CacheManager.onServerStopping(event.getServer().getAllLevels());
        SignTextService.clearPending();
        tick = 0;
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CacheManager.onDimensionUnload(level);
        }
    }
}

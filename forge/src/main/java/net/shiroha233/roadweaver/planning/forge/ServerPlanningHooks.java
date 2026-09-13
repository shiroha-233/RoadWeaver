/* 文件职责：注册 Forge 服务端生命周期、维度卸载与 Tick 钩子。 */
package net.shiroha233.roadweaver.planning.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
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

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onLevelUnload);
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

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
        RoadGenerationService.onServerStopping();
        RoadPlanningService.resetAll();
        ThreadPoolManager.onServerStopping();
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

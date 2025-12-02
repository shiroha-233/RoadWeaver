package net.shiroha233.roadweaver.planning.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.util.ComputeService;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.structures.StructureSystem;
import net.shiroha233.roadweaver.structures.index.StructureIndexRestorer;

import java.util.Objects;

public final class ServerPlanningHooks {
    private ServerPlanningHooks() {}

    private static int tick;

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStopping);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        StructureSystem.clearAll();
        net.shiroha233.roadweaver.runtime.ThreadPoolManager.onServerStarted(event.getServer());
        ServerLevel level = event.getServer().getLevel(Objects.requireNonNull(Level.OVERWORLD));
        if (level == null) return;
        // 从持久化数据恢复结构索引
        StructureIndexRestorer.restore(level);
        boolean dedicated = event.getServer().isDedicatedServer();
        if (dedicated) {
            RoadGenerationService.onServerStarted();
            RoadPlanningService.initialPlanAsync(level);
            return;
        }
        java.util.List<net.shiroha233.roadweaver.helpers.Records.StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) {
            InitialGenManager.begin(level);
            InitialGenManager.blockUntilDone(level);
        } else {
            RoadGenerationService.onServerStarted();
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        if (server == null) return;
        if ((tick++ % 20) == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                RoadPlanningService.planAroundPlayer(p);
            }
        }
        ServerLevel level = server.getLevel(Objects.requireNonNull(Level.OVERWORLD));
        if (level != null) {
            RoadGenerationService.tick(level);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        var server = event.getServer();
        for (ServerLevel lvl : server.getAllLevels()) {
            RoadShardStorage.flushAll(lvl);
            RoadShardStorage.clearAll(lvl);
        }
        RoadGenerationService.onServerStopping();
        StructureSystem.clearAll();
        ComputeService.shutdownNow();
    }
}

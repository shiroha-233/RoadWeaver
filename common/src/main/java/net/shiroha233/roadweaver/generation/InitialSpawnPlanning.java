package net.shiroha233.roadweaver.generation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.structures.placement.SpawnCabinPlacer;

/**
 * When the initial road network is planned, called from the MinecraftServer mixins of both loaders.
 * <p>
 * On a new world vanilla fully generates the chunks around spawn inside MinecraftServer.setInitialSpawn, while it
 * searches for a spawn point, and that runs before prepareLevels. Planning in prepareLevels left those chunks without
 * roads and without the spawn cabin. New worlds are therefore planned at the start of setInitialSpawn, around the
 * point where vanilla starts its search, and the cabin is built once setInitialSpawn returns. Existing worlds keep the
 * prepareLevels path and get no cabin: the area around their spawn has long been explored and may be built on.
 */
public final class InitialSpawnPlanning {
    private static volatile boolean initialRunDone;

    private InitialSpawnPlanning() {}

    public static void beforeSpawnSearch(ServerLevel level, ServerLevelData levelData) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        RoadShardStorage.preload(level);
        if (!InitialGenManager.shouldRunInitialGeneration(level)) return;
        // Plan around the point where vanilla starts its spawn search; setInitialSpawn overwrites this right after.
        levelData.setSpawn(SpawnCabinPlacer.predictSpawn(level), 0.0F);
        InitialGenManager.begin(level);
        InitialGenManager.blockUntilDone(level);
        initialRunDone = true;
    }

    public static void afterSpawnSearch(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        SpawnCabinPlacer.placePlannedCabin(level);
    }

    public static void beforePrepareLevels(MinecraftServer server) {
        if (initialRunDone) {
            initialRunDone = false;
            return;
        }
        ServerLevel level = server.overworld();
        if (level == null) return;
        RoadShardStorage.preload(level);
        if (InitialGenManager.shouldRunInitialGeneration(level)) {
            InitialGenManager.begin(level);
            InitialGenManager.blockUntilDone(level);
        } else {
            RoadGenerationService.onServerStarted();
        }
    }
}

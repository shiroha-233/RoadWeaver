package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import java.util.LinkedHashMap;
import java.util.List;

final class LegacyDirectPlanningAdapter implements RoadTerrainPlanningPort {
    @Override
    public Result plan(Request request) {
        if (request.connections().isEmpty()) {
            return Result.empty(TerrainSamplingMode.LEGACY_DIRECT);
        }

        TerrainSamplingCache cache = new TerrainSamplingCache();
        PathTerrainField terrain = new CacheBackedTerrainField(request.level(), cache,
                request.pathfinding().effectiveAStarStep());
        LinkedHashMap<StructureConnection, List<net.minecraft.core.BlockPos>> paths = new LinkedHashMap<>();
        try {
            for (StructureConnection connection : request.connections()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("direct planning interrupted");
                }
                List<net.minecraft.core.BlockPos> path = RawPathSearch.find(
                        request.level(), connection, request.pathfinding(), cache, terrain);
                if (path != null && !path.isEmpty()) {
                    paths.put(connection, path);
                }
            }
            return new Result(TerrainSamplingMode.LEGACY_DIRECT, paths);
        } finally {
            cache.clear();
        }
    }

    private static final class CacheBackedTerrainField implements PathTerrainField {
        private final ServerLevel level;
        private final TerrainSamplingCache cache;
        private final int step;

        private CacheBackedTerrainField(ServerLevel level, TerrainSamplingCache cache, int step) {
            this.level = level;
            this.cache = cache;
            this.step = step;
        }

        @Override
        public int seaLevel() {
            return level.getSeaLevel();
        }

        @Override
        public int height(int x, int z) {
            return cache.height(level, x, z);
        }

        @Override
        public int oceanFloor(int x, int z) {
            return cache.oceanFloor(level, x, z);
        }

        @Override
        public boolean isColumnWater(int x, int z) {
            return cache.isColumnWater(level, x, z);
        }

        @Override
        public boolean isNearWater(int x, int z, int neighborDistance) {
            return cache.isNearWaterLike(level, x, z, neighborDistance);
        }

        @Override
        public Holder<Biome> biome(int x, int z) {
            return cache.getBiome(level, x, z);
        }

        @Override
        public boolean contains(int x, int z) {
            return true;
        }

        @Override
        public int step() {
            return step;
        }

        @Override
        public SampleBundle sampleBundle(int x, int z) {
            int surface = height(x, z);
            int oceanFloor = oceanFloor(x, z);
            boolean columnWater = isColumnWater(x, z);
            Holder<Biome> biome = biome(x, z);
            boolean waterBiome = biome != null && (biome.is(BiomeTags.IS_RIVER)
                    || biome.is(BiomeTags.IS_OCEAN)
                    || biome.is(BiomeTags.IS_DEEP_OCEAN));
            return new SampleBundle(surface, oceanFloor, columnWater, waterBiome,
                    Math.max(0, seaLevel() - oceanFloor));
        }
    }
}

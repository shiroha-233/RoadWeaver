package net.shiroha233.roadweaver.structures.placement;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 初始小屋放置器。
 * Vanilla generates the chunks around spawn while it searches for a spawn point, so a cabin queued as a pending
 * structure could never be injected into them. ensurePlaced() only registers the anchor as a road endpoint (without
 * loading chunks); placePlannedCabin() builds the cabin into the world once the spawn chunks exist.
 */
public final class SpawnCabinPlacer {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private SpawnCabinPlacer() {}

    private static final ResourceLocation STRUCTURE_ID = ResourceLocation.fromNamespaceAndPath("roadweaver", "spawn_cabin");

    /** Anchor registered by ensurePlaced() and not built yet. */
    private static final AtomicReference<BlockPos> PLANNED_ANCHOR = new AtomicReference<>();

    /** How far from the planned anchor the cabin may move to stay clear of other structures and the road. */
    private static final int SEARCH_RADIUS = 32;
    private static final int SEARCH_STEP = 4;
    /** Gap kept between the cabin and other structure pieces. */
    private static final int CLEARANCE = 2;
    /** Largest difference in ground height under the cabin that still counts as level. */
    private static final int MAX_GROUND_SPREAD = 1;

    /**
     * Where vanilla starts its spawn search (MinecraftServer.setInitialSpawn): the middle of the chunk at the climate
     * sampler's spawn position. The height comes from the noise only, so no chunk is generated.
     */
    public static BlockPos predictSpawn(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        ChunkPos chunk = new ChunkPos(randomState.sampler().findSpawnPosition());
        int x = chunk.getMinBlockX() + 8;
        int z = chunk.getMinBlockZ() + 8;
        int y = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, randomState);
        return new BlockPos(x, y, z);
    }

    public static boolean ensurePlaced(ServerLevel level) {
        if (level == null) return false;

        BlockPos spawn = level.getSharedSpawnPos();

        var provider = WorldDataProvider.getInstance();
        var locs = provider.getStructureLocations(level);
        if (locs != null && locs.structureLocations() != null && !locs.structureLocations().isEmpty()) {
            return false;
        }

        if (!(spawnCabin(level) instanceof SpawnCabinStructure)) {
            return false;
        }

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int y = generator.getBaseHeight(spawn.getX(), spawn.getZ(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                level, level.getChunkSource().randomState());
        BlockPos anchor = new BlockPos(spawn.getX(), y, spawn.getZ());
        PLANNED_ANCHOR.set(anchor);

        provider.addStructureLocation(level, anchor);
        StructureFileStorage.addStructures(
                level,
                java.util.List.of(new StructureInfo(new BlockPos(anchor.getX(), 0, anchor.getZ()), STRUCTURE_ID.toString())),
                StructureFileStorage.SOURCE_MANUAL
        );

        return true;
    }

    /**
     * Builds the cabin registered by ensurePlaced() directly into the world. Call it after the initial roads are
     * planned and the spawn chunks exist; the chunks under the cabin are loaded if needed.
     */
    public static void placePlannedCabin(ServerLevel level) {
        BlockPos anchor = PLANNED_ANCHOR.getAndSet(null);
        if (level == null || anchor == null) return;
        if (!(spawnCabin(level) instanceof SpawnCabinStructure cabin)) return;
        Vec3i size = level.getStructureManager().get(cabin.templateId())
                .map(t -> t.getSize()).orElse(cabin.sizeHint());
        BlockPos pos = findFreeSpot(level, anchor, size);
        if (pos == null) {
            LOGGER.warn("No free spot for the spawn cabin within {} blocks of {}, skipping it", SEARCH_RADIUS, anchor);
            return;
        }
        StructureStart start = cabin.placeAt(level, level.structureManager(), level.getStructureManager(),
                level.getChunkSource().getGenerator(), pos, Rotation.NONE, level.getRandom());
        if (start == null) {
            LOGGER.warn("Spawn cabin could not be placed at {}", pos);
        } else {
            LOGGER.info("Spawn cabin placed at {} (world spawn {})", pos, level.getSharedSpawnPos());
        }
    }

    /**
     * Spot near {@code anchor} (spiral in {@link #SEARCH_STEP} block steps) where the cabin footprint touches no
     * structure piece and no road, preferring the nearest one whose ground is level within {@link #MAX_GROUND_SPREAD}
     * blocks; on uneven ground the cabin is set into the slope and the template clears a pit around it. Without a
     * level spot the flattest free one is used. The returned position has the cabin's foundation layer at the median
     * ground height of its footprint.
     */
    private static BlockPos findFreeSpot(ServerLevel level, BlockPos anchor, Vec3i size) {
        BlockPos flattest = null;
        int flattestSpread = Integer.MAX_VALUE;
        for (int ring = 0; ring <= SEARCH_RADIUS / SEARCH_STEP; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = anchor.getX() + dx * SEARCH_STEP;
                    int z = anchor.getZ() + dz * SEARCH_STEP;
                    int[] heights = footprintHeights(level, x, z, size);
                    int y = heights[heights.length / 2];
                    int spread = heights[heights.length - 1] - heights[0];
                    BoundingBox box = new BoundingBox(x, y, z, x + size.getX() - 1, y + size.getY() - 1, z + size.getZ() - 1);
                    if (touchesStructure(level, box.inflatedBy(CLEARANCE)) || touchesRoad(level, box)) continue;
                    if (spread <= MAX_GROUND_SPREAD) return new BlockPos(x, y, z);
                    if (spread < flattestSpread) {
                        flattestSpread = spread;
                        flattest = new BlockPos(x, y, z);
                    }
                }
            }
        }
        return flattest;
    }

    /** Ground heights over the footprint, every second block, sorted. */
    private static int[] footprintHeights(ServerLevel level, int x, int z, Vec3i size) {
        int nx = (size.getX() + 1) / 2;
        int nz = (size.getZ() + 1) / 2;
        int[] heights = new int[nx * nz];
        int i = 0;
        for (int a = 0; a < nx; a++) {
            for (int b = 0; b < nz; b++) {
                heights[i++] = groundTop(level, x + Math.min(a * 2, size.getX() - 1), z + Math.min(b * 2, size.getZ() - 1));
            }
        }
        Arrays.sort(heights);
        return heights;
    }

    /**
     * Top ground block at x/z. Level.getHeight answers the minimum height for chunks that are not loaded, so the chunk
     * is loaded (generated if needed) first; this mirrors Level.getHeight without the "+ 1".
     */
    private static int groundTop(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    private static boolean touchesStructure(ServerLevel level, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_REFERENCES);
                for (Map.Entry<Structure, LongSet> ref : chunk.getAllReferences().entrySet()) {
                    for (long startChunk : ref.getValue()) {
                        ChunkAccess startAccess = level.getChunk(ChunkPos.getX(startChunk), ChunkPos.getZ(startChunk),
                                ChunkStatus.STRUCTURE_STARTS);
                        StructureStart start = startAccess.getStartForStructure(ref.getKey());
                        if (start == null || !start.isValid()) continue;
                        for (StructurePiece piece : start.getPieces()) {
                            if (piece.getBoundingBox().intersects(box)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean touchesRoad(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (RoadPositionQuery.isOnRoad(level, pos.set(x, box.minY(), z))) return true;
            }
        }
        return false;
    }

    private static Structure spawnCabin(ServerLevel level) {
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return structureRegistry.get(STRUCTURE_ID);
    }
}

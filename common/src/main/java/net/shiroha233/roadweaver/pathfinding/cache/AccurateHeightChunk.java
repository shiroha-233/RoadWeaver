package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.world.level.ChunkPos;

/**
 * 单个区块的精确高度采样结果。
 */
public final class AccurateHeightChunk {
    public static final int COLUMN_COUNT = 16 * 16;

    private final int chunkX;
    private final int chunkZ;
    private final int[] worldSurfaceWg;
    private final int[] oceanFloorWg;
    private final int[] motionBlockingNoLeaves;

    public AccurateHeightChunk(int chunkX,
                               int chunkZ,
                               int[] worldSurfaceWg,
                               int[] oceanFloorWg,
                               int[] motionBlockingNoLeaves) {
        requireColumns(worldSurfaceWg, "worldSurfaceWg");
        requireColumns(oceanFloorWg, "oceanFloorWg");
        requireColumns(motionBlockingNoLeaves, "motionBlockingNoLeaves");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.worldSurfaceWg = worldSurfaceWg;
        this.oceanFloorWg = oceanFloorWg;
        this.motionBlockingNoLeaves = motionBlockingNoLeaves;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public long chunkKey() {
        return ChunkPos.asLong(chunkX, chunkZ);
    }

    public int worldSurfaceWg(int blockX, int blockZ) {
        return worldSurfaceWg[localIndex(blockX, blockZ)];
    }

    public int oceanFloorWg(int blockX, int blockZ) {
        return oceanFloorWg[localIndex(blockX, blockZ)];
    }

    public int motionBlockingNoLeaves(int blockX, int blockZ) {
        return motionBlockingNoLeaves[localIndex(blockX, blockZ)];
    }

    public int worldSurfaceWgAt(int index) {
        return worldSurfaceWg[checkedIndex(index)];
    }

    public int oceanFloorWgAt(int index) {
        return oceanFloorWg[checkedIndex(index)];
    }

    public int motionBlockingNoLeavesAt(int index) {
        return motionBlockingNoLeaves[checkedIndex(index)];
    }

    private static int localIndex(int blockX, int blockZ) {
        return (blockX & 15) + ((blockZ & 15) << 4);
    }

    private static int checkedIndex(int index) {
        if (index < 0 || index >= COLUMN_COUNT) {
            throw new IndexOutOfBoundsException("height column index: " + index);
        }
        return index;
    }

    private static void requireColumns(int[] values, String name) {
        if (values == null || values.length != COLUMN_COUNT) {
            throw new IllegalArgumentException(name + " must contain " + COLUMN_COUNT + " columns");
        }
    }
}

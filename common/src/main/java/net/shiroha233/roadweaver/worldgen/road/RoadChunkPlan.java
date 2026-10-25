package net.shiroha233.roadweaver.worldgen.road;

import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * 世界生成阶段共享的不可变区块计划。
 */
public final class RoadChunkPlan {
    private final ChunkPos chunkPos;
    private final List<RoadChunkSlice> slices;
    private final RoadDensityStamp densityStamp;
    private final long revision;

    private RoadChunkPlan(ChunkPos chunkPos,
                          List<RoadChunkSlice> slices,
                          RoadDensityStamp densityStamp,
                          long revision) {
        this.chunkPos = chunkPos;
        this.slices = List.copyOf(slices);
        this.densityStamp = densityStamp;
        this.revision = revision;
    }

    public static RoadChunkPlan empty(ChunkPos chunkPos, int clearHeight, long revision) {
        return new RoadChunkPlan(chunkPos, List.of(), RoadDensityStamp.empty(chunkPos, clearHeight), revision);
    }

    public static RoadChunkPlan of(ChunkPos chunkPos,
                                   List<RoadChunkSlice> slices,
                                   RoadDensityStamp densityStamp,
                                   long revision) {
        if (chunkPos == null || densityStamp == null) {
            throw new IllegalArgumentException("chunk plan requires a position and density stamp");
        }
        if (!chunkPos.equals(densityStamp.chunkPos())) {
            throw new IllegalArgumentException("chunk plan and density stamp positions must match");
        }
        return new RoadChunkPlan(chunkPos, slices == null ? List.of() : slices, densityStamp, revision);
    }

    public ChunkPos chunkPos() {
        return chunkPos;
    }

    public List<RoadChunkSlice> slices() {
        return slices;
    }

    public RoadDensityStamp densityStamp() {
        return densityStamp;
    }

    public long revision() {
        return revision;
    }

    public boolean isEmpty() {
        return slices.isEmpty() && densityStamp.isEmpty();
    }
}

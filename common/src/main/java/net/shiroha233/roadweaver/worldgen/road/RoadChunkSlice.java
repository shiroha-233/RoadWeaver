package net.shiroha233.roadweaver.worldgen.road;

import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.core.model.RoadData;

import java.util.Arrays;

/**
 * 区块局部道路切片。segmentIndices 使用 RoadData 原始索引，避免复制整条道路。
 */
public final class RoadChunkSlice {
    private final RoadData road;
    private final int[] segmentIndices;
    private final ChunkPos chunkPos;

    RoadChunkSlice(RoadData road, ChunkPos chunkPos, int[] segmentIndices) {
        this.road = road;
        this.chunkPos = chunkPos;
        this.segmentIndices = segmentIndices.clone();
    }

    public RoadData road() {
        return road;
    }

    public ChunkPos chunkPos() {
        return chunkPos;
    }

    public int segmentCount() {
        return segmentIndices.length;
    }

    public int segmentIndexAt(int offset) {
        return segmentIndices[offset];
    }

    public boolean isEmpty() {
        return segmentIndices.length == 0;
    }

    @Override
    public String toString() {
        return "RoadChunkSlice{" + chunkPos + ", segments=" + Arrays.toString(segmentIndices) + '}';
    }
}

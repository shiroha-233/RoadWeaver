package net.countered.settlementroads.helpers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

public class Records {

    public record WoodAssets(Block fence, Block hangingSign, Block planks) {}

    public record RoadDecoration(BlockPos placePos, Vec3i vector, int centerBlockCount, String signText, boolean isStart) {}

    public record RoadData(int width, int roadType, List<BlockState> materials, List<RoadSegmentPlacement> roadSegmentList) {
        public static final Codec<RoadData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("width").forGetter(RoadData::width),
                Codec.INT.fieldOf("road_type").forGetter(RoadData::roadType),
                BlockState.CODEC.listOf().fieldOf("materials").forGetter(RoadData::materials),
                RoadSegmentPlacement.CODEC.listOf().fieldOf("placements").forGetter(RoadData::roadSegmentList)
        ).apply(instance, RoadData::new));
    }

    public record RoadSegmentPlacement(BlockPos middlePos, List<BlockPos> positions) {
        public static final Codec<RoadSegmentPlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("middle_pos").forGetter(RoadSegmentPlacement::middlePos),
                BlockPos.CODEC.listOf().fieldOf("positions").forGetter(RoadSegmentPlacement::positions)
        ).apply(instance, RoadSegmentPlacement::new));
    }

    public record StructureLocationData(List<BlockPos> structureLocations) {
        public StructureLocationData(List<BlockPos> structureLocations) {
            this.structureLocations = new ArrayList<>(structureLocations); // mutable copy
        }

        public void addStructure(BlockPos pos) {
            structureLocations.add(pos);
        }

        public static final Codec<StructureLocationData> CODEC = BlockPos.CODEC
                .listOf()
                .xmap(StructureLocationData::new, StructureLocationData::structureLocations);
    }
    public record StructureConnection(BlockPos from, BlockPos to, ConnectionStatus status) {
        public static final Codec<StructureConnection> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        BlockPos.CODEC.fieldOf("from").forGetter(StructureConnection::from),
                        BlockPos.CODEC.fieldOf("to").forGetter(StructureConnection::to),
                        Codec.STRING.optionalFieldOf("status", "PLANNED").xmap(
                                ConnectionStatus::valueOf,
                                ConnectionStatus::name
                        ).forGetter(StructureConnection::status)
                ).apply(instance, StructureConnection::new)
        );
        
        // 兼容旧版本的构造函数
        public StructureConnection(BlockPos from, BlockPos to) {
            this(from, to, ConnectionStatus.PLANNED);
        }
    }
    
    public enum ConnectionStatus {
        PLANNED,      // 计划中（黄色）
        GENERATING,   // 生成中（橙色）
        COMPLETED,    // 已完成（绿色，不显示连接线）
        FAILED        // 生成失败（红色）
    }
}

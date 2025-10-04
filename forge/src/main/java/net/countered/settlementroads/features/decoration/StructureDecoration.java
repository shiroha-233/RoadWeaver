package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.decoration.util.BiomeWoodAware;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 结构装饰基类，用于处理NBT结构文件的加载和放置
 */
public abstract class StructureDecoration extends OrientedDecoration implements BiomeWoodAware {
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver");

    protected Records.WoodAssets wood;
    private final String structureName;
    private final Vec3i structureSize;

    public StructureDecoration(BlockPos pos, Vec3i direction, WorldGenLevel world, String structureName, Vec3i structureSize) {
        super(pos, direction, world);
        this.structureName = structureName;
        this.structureSize = structureSize;
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;

        // 检查空间是否足够
        if (!hasEnoughSpace()) {
            return;
        }

        // 加载并放置结构
        StructureTemplate template = loadStructureTemplate();
        if (template != null) {
            placeStructure(template);
        } else {
            // 如果NBT加载失败，使用代码生成的备用结构
            placeFallbackStructure();
        }
    }

    /**
     * 检查结构放置空间是否足够
     */
    protected boolean hasEnoughSpace() {
        BlockPos basePos = getPos();
        WorldGenLevel world = getWorld();

        // 检查结构范围内的地面高度差
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = 0; x < structureSize.getX(); x++) {
            for (int z = 0; z < structureSize.getZ(); z++) {
                BlockPos checkPos = basePos.offset(x, 0, z);
                int groundY = this.getSurfaceY(checkPos.getX(), checkPos.getZ());
                minY = Math.min(minY, groundY);
                maxY = Math.max(maxY, groundY);
            }
        }

        // 如果地面高度差超过2格，不适合放置大型结构
        return (maxY - minY) <= 2;
    }

    /**
     * 加载NBT结构模板
     */
    protected StructureTemplate loadStructureTemplate() {
        try {
            ResourceLocation structureId = new ResourceLocation("roadweaver", "structures/" + structureName);
            StructureTemplate template = new StructureTemplate();

            // 尝试从资源包加载NBT文件
            String path = "/assets/roadweaver/structures/" + structureName + ".nbt";
            InputStream inputStream = getClass().getResourceAsStream(path);

            if (inputStream != null) {
                CompoundTag nbt = NbtIo.readCompressed(inputStream);
                template.load(this.getWorld().getLevel().registryAccess().lookupOrThrow(ForgeRegistries.Keys.BLOCKS), nbt);
                return template;
            } else {
                // 如果在assets中没找到，可能在data目录中
                String dataPath = "/data/roadweaver/structures/" + structureName + ".nbt";
                inputStream = getClass().getResourceAsStream(dataPath);

                if (inputStream != null) {
                    CompoundTag nbt = NbtIo.readCompressed(inputStream);
                    template.load(this.getWorld().getLevel().registryAccess().lookupOrThrow(ForgeRegistries.Keys.BLOCKS), nbt);
                    return template;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load structure: " + structureName + ", using fallback. Error: " + e.getMessage());
        }
        return null;
    }

    /**
     * 放置NBT结构
     */
    protected void placeStructure(StructureTemplate template) {
        BlockPos placePos = getPos();
        WorldGenLevel world = getWorld();

        // 找到合适的地面位置
        BlockPos groundLevel = findGroundLevel(placePos, world);

        // 设置结构放置参数
        StructurePlaceSettings placementData = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(getRotationFromDirection())
                .setIgnoreEntities(true); // 忽略实体

        // 在地面位置放置结构
        template.placeInWorld(world, groundLevel, groundLevel, placementData, world.getRandom(), 2);

        // 额外处理：移除不需要的空气方块覆盖
        cleanupAirBlocks(template, groundLevel, placementData);
    }

    /**
     * 找到结构的合适地面位置
     */
    protected BlockPos findGroundLevel(BlockPos basePos, WorldGenLevel world) {
        // 检查结构占用区域的最低地面高度
        int minY = Integer.MAX_VALUE;
        Vec3i size = structureSize;

        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                BlockPos checkPos = basePos.offset(x - size.getX()/2, 0, z - size.getZ()/2);
                int groundY = this.getSurfaceY(checkPos.getX(), checkPos.getZ());
                minY = Math.min(minY, groundY);
            }
        }

        // NBT结构包含草皮，需要比地面低一格生成
        return new BlockPos(basePos.getX(), minY - 1, basePos.getZ());
    }

    /**
     * 清理空气方块覆盖问题
     */
    protected void cleanupAirBlocks(StructureTemplate template, BlockPos basePos, StructurePlaceSettings placementData) {
        List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo> blocks =
                template.filterBlocks(basePos, placementData, Blocks.AIR);

        for (net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo blockInfo : blocks) {
            BlockPos airPos = new BlockPos(blockInfo.pos());
            net.minecraft.world.level.block.state.BlockState currentState = getWorld().getBlockState(airPos);

            // 只有当前位置是需要清理的方块时才设置为空气
            if (shouldReplaceWithAir(currentState)) {
                this.setBlock(airPos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * 判断是否应该被空气替换
     */
    protected boolean shouldReplaceWithAir(net.minecraft.world.level.block.state.BlockState state) {
        // 只替换草、花、小植物等，不替换重要方块
        return state.is(Blocks.GRASS) ||
               state.is(Blocks.TALL_GRASS) ||
               state.is(Blocks.FERN) ||
               state.is(Blocks.LARGE_FERN) ||
               state.is(net.minecraft.tags.BlockTags.FLOWERS) ||
               state.is(net.minecraft.tags.BlockTags.SAPLINGS);
    }

    /**
     * 根据道路方向获取旋转角度，确保结构与道路平行
     */
    protected Rotation getRotationFromDirection() {
        // 获取道路方向向量（而不是垂直向量）
        Vec3i roadDirection = getRoadDirection();

        // 确保结构沿着道路方向放置
        if (Math.abs(roadDirection.getX()) > Math.abs(roadDirection.getZ())) {
            // 遹路是东西向，结构也应该东西向
            return roadDirection.getX() > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        } else {
            // 遹路是南北向，结构也应该南北向
            return roadDirection.getZ() > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
        }
    }

    /**
     * 获取道路方向向量（orthogonalVector的垂直方向就是道路方向）
     */
    protected Vec3i getRoadDirection() {
        Vec3i orthogonal = getOrthogonalVector();
        // 将垂直向量转换为道路方向向量
        return new Vec3i(-orthogonal.getZ(), 0, orthogonal.getX());
    }

    /**
     * 备用结构生成（当NBT文件加载失败时使用）
     */
    protected abstract void placeFallbackStructure();

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }

    public Vec3i getStructureSize() {
        return structureSize;
    }
}

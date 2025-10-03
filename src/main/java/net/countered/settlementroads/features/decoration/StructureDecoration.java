package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.decoration.util.BiomeWoodAware;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.StructureWorldAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    public StructureDecoration(BlockPos pos, Vec3i direction, StructureWorldAccess world, String structureName, Vec3i structureSize) {
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
        StructureWorldAccess world = getWorld();
        
        // 检查结构范围内的地面高度差
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        
        for (int x = 0; x < structureSize.getX(); x++) {
            for (int z = 0; z < structureSize.getZ(); z++) {
                BlockPos checkPos = basePos.add(x, 0, z);
                int groundY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, checkPos.getX(), checkPos.getZ());
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
            Identifier structureId = Identifier.of("roadweaver", "structures/" + structureName);
            StructureTemplate template = new StructureTemplate();
            
            // 尝试从资源包加载NBT文件
            InputStream inputStream = getClass().getResourceAsStream("/data/roadweaver/structures/" + structureName + ".nbt");
            if (inputStream != null) {
                NbtCompound nbt = NbtIo.readCompressed(inputStream, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
                template.readNbt(getWorld().getRegistryManager().getWrapperOrThrow(net.minecraft.registry.RegistryKeys.BLOCK), nbt);
                return template;
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
        StructureWorldAccess world = getWorld();
        
        // 找到合适的地面位置
        BlockPos groundLevel = findGroundLevel(placePos, world);
        
        // 设置结构放置参数
        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(getRotationFromDirection())
                .setIgnoreEntities(true); // 忽略实体
        
        // 在地面位置放置结构
        template.place(world, groundLevel, groundLevel, placementData, world.getRandom(), 2);
        
        // 额外处理：移除不需要的空气方块覆盖
        cleanupAirBlocks(template, groundLevel, placementData);
    }
    
    /**
     * 找到结构的合适地面位置
     */
    protected BlockPos findGroundLevel(BlockPos basePos, StructureWorldAccess world) {
        // 检查结构占用区域的最低地面高度
        int minY = Integer.MAX_VALUE;
        Vec3i size = structureSize;
        
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                BlockPos checkPos = basePos.add(x - size.getX()/2, 0, z - size.getZ()/2);
                int groundY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, checkPos.getX(), checkPos.getZ());
                minY = Math.min(minY, groundY);
            }
        }
        
        // NBT结构包含草皮，需要比地面低一格生成
        return new BlockPos(basePos.getX(), minY - 1, basePos.getZ());
    }
    
    /**
     * 清理空气方块覆盖问题
     */
    protected void cleanupAirBlocks(StructureTemplate template, BlockPos basePos, StructurePlacementData placementData) {
        List<StructureTemplate.StructureBlockInfo> blocks = template.getInfosForBlock(basePos, placementData, Blocks.AIR);
        
        for (StructureTemplate.StructureBlockInfo blockInfo : blocks) {
            BlockPos airPos = blockInfo.pos();
            BlockState currentState = getWorld().getBlockState(airPos);
            
            // 只有当前位置是需要清理的方块时才设置为空气
            if (shouldReplaceWithAir(currentState)) {
                getWorld().setBlockState(airPos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }
    
    /**
     * 判断是否应该被空气替换
     */
    protected boolean shouldReplaceWithAir(BlockState state) {
        // 只替换草、花、小植物等，不替换重要方块
        return state.getBlock().equals(Blocks.SHORT_GRASS) || 
               state.getBlock().equals(Blocks.TALL_GRASS) ||
               state.getBlock().equals(Blocks.FERN) ||
               state.getBlock().equals(Blocks.LARGE_FERN) ||
               state.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS) ||
               state.isIn(net.minecraft.registry.tag.BlockTags.SAPLINGS);
    }
    
    /**
     * 根据道路方向获取旋转角度，确保结构与道路平行
     */
    protected BlockRotation getRotationFromDirection() {
        // 获取道路方向向量（而不是垂直向量）
        Vec3i roadDirection = getRoadDirection();
        
        // 确保结构沿着道路方向放置
        if (Math.abs(roadDirection.getX()) > Math.abs(roadDirection.getZ())) {
            // 道路是东西向，结构也应该东西向
            return roadDirection.getX() > 0 ? BlockRotation.NONE : BlockRotation.CLOCKWISE_180;
        } else {
            // 道路是南北向，结构也应该南北向
            return roadDirection.getZ() > 0 ? BlockRotation.CLOCKWISE_90 : BlockRotation.COUNTERCLOCKWISE_90;
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

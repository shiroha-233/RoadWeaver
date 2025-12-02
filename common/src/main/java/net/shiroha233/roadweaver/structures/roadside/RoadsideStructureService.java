package net.shiroha233.roadweaver.structures.roadside;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.structures.StructureSystem;
import net.shiroha233.roadweaver.structures.pipeline.StructurePlacer;

import java.util.List;

/**
 * 路边结构放置服务
 * 
 * 职责：
 * 1. 根据群系和道路长度选择可放置的结构类型（硬编码规则）
 * 2. 根据配置控制每条道路的最大结构数和最小间距
 * 3. 根据结构规模使用配置的距离偏移
 * 4. 计算放置位置和朝向
 * 
 * 设计说明：
 * - 群系限制和道路长度限制是硬编码的（RoadsidePlacementRule）
 * - 结构数量和间距通过配置界面调整（ModConfig）
 * - 使用 RoadPlacementContext 跟踪单条道路的放置状态
 */
public final class RoadsideStructureService {
    private RoadsideStructureService() {}
    
    // 额外的随机偏移范围
    private static final int RANDOM_OFFSET_RANGE = 3;
    
    /**
     * 尝试在指定路段旁放置路边结构
     * 
     * @param world       世界
     * @param server      服务端世界
     * @param middlePos   路段中心位置
     * @param prevPos     前一个路段位置
     * @param nextPos     后一个路段位置
     * @param roadWidth   道路宽度
     * @param roadLength  道路总长度（路段数）
     * @param ctx         道路放置上下文
     * @param random      随机源
     * @param cfg         模组配置
     * @return 如果成功放置则返回 true
     */
    public static boolean tryPlace(WorldGenLevel world,
                                   ServerLevel server,
                                   BlockPos middlePos,
                                   BlockPos prevPos,
                                   BlockPos nextPos,
                                   int roadWidth,
                                   int roadLength,
                                   RoadPlacementContext ctx,
                                   RandomSource random,
                                   ModConfig cfg) {
        // 检查是否启用路边结构
        if (!cfg.roadsideStructuresEnabled()) {
            return false;
        }
        
        // 检查是否达到每条路的最大结构数
        if (ctx.isMaxReached(cfg.maxStructuresPerRoad())) {
            return false;
        }
        
        // 获取当前位置的群系分类
        Holder<Biome> biome = world.getBiome(middlePos);
        BiomeCategory biomeCategory = BiomeCategory.fromBiome(biome);
        
        // 根据群系和道路长度过滤后，按权重选择结构类型
        RoadsideType type = RoadsideType.chooseWeightedFiltered(random, biomeCategory, roadLength);
        if (type == null) {
            return false;  // 当前条件下没有可放置的结构
        }
        
        // 计算道路方向向量
        int dx = nextPos.getX() - prevPos.getX();
        int dz = nextPos.getZ() - prevPos.getZ();
        double len = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (len < 0.001) {
            return false;
        }
        
        double dirX = dx / len;
        double dirZ = dz / len;
        double orthoX = -dirZ;
        double orthoZ = dirX;
        
        // 随机选择左侧或右侧
        boolean leftSide = random.nextBoolean();
        double sideMultiplier = leftSide ? 1.0 : -1.0;
        
        // 根据结构规模获取配置的侧向偏移
        int halfWidth = Math.max(1, roadWidth / 2);
        int sideOffset = halfWidth + getOffsetForScale(type.scale(), cfg) 
                       + random.nextInt(RANDOM_OFFSET_RANGE + 1);
        
        // 计算放置位置
        int placeX = middlePos.getX() + (int) Math.round(orthoX * sideOffset * sideMultiplier);
        int placeZ = middlePos.getZ() + (int) Math.round(orthoZ * sideOffset * sideMultiplier);
        
        // 采样地面高度
        Vec3i sizeHint = type.sizeHint();
        int halfSizeX = sizeHint.getX() / 2;
        int halfSizeZ = sizeHint.getZ() / 2;
        int[] sampleHeights = sampleGroundHeights(world, placeX, placeZ, halfSizeX, halfSizeZ);
        int placeY = sampleHeights[0];
        int slopeHeight = sampleHeights[0] - sampleHeights[1];
        
        StructureScale scale = type.scale();
        
        // 地形检查
        if (slopeHeight > scale.maxSlope()) {
            return false;
        }
        
        BlockPos placePos = new BlockPos(placeX, placeY, placeZ);
        
        // 检查是否在水上（禁止放置）
        if (isOnWater(world, placeX, placeY, placeZ, halfSizeX, halfSizeZ)) {
            return false;
        }
        
        int heightDiff = Math.abs(placeY - middlePos.getY());
        if (heightDiff > scale.maxHeightDiff()) {
            return false;
        }
        
        // 检查与已放置结构的间距（使用配置的最小间距）
        if (!ctx.checkSpacing(placePos, cfg.minStructureSpacing())) {
            return false;
        }
        
        // 检查全局结构索引（防止与其他道路的结构重叠）
        if (StructureSystem.index(server).existsNear(placePos, cfg.minStructureSpacing())) {
            return false;
        }
        
        // 计算朝向
        Rotation rotation = calculateRotation(dirX, dirZ, leftSide, type.faceRoad());
        
        // 放置结构
        ResourceLocation templateId = type.templateId();
        boolean placed = StructurePlacer.placeSimple(world, server, templateId, placePos, rotation,
                                                      true, true, true, random);
        
        if (placed) {
            ctx.recordPlacement(placePos);
        }
        
        return placed;
    }
    
    /**
     * 根据结构规模获取配置的偏移距离
     */
    private static int getOffsetForScale(StructureScale scale, ModConfig cfg) {
        return switch (scale) {
            case SMALL -> cfg.smallStructureOffset();
            case MEDIUM -> cfg.mediumStructureOffset();
            case LARGE -> cfg.largeStructureOffset();
        };
    }
    
    /**
     * 批量处理路段，尝试放置路边结构（独立使用）
     * 
     * 注意：RoadFeature 中使用 tryPlace + 外部检查间隔的方式，
     * 此方法适用于独立调用场景。
     * 
     * @param world           世界
     * @param server          服务端世界
     * @param middlePositions 所有路段中心位置列表
     * @param roadWidth       道路宽度
     * @param random          随机源
     * @param cfg             配置
     * @param startIndex      起始索引
     * @param endIndex        结束索引
     */
    public static void processSegments(WorldGenLevel world,
                                       ServerLevel server,
                                       List<BlockPos> middlePositions,
                                       int roadWidth,
                                       RandomSource random,
                                       ModConfig cfg,
                                       int startIndex,
                                       int endIndex) {
        if (middlePositions == null || middlePositions.size() < 5) {
            return;
        }
        
        int roadLength = middlePositions.size();
        RoadPlacementContext ctx = new RoadPlacementContext(roadLength);
        
        int safeStart = Math.max(2, startIndex);
        int safeEnd = Math.min(middlePositions.size() - 3, endIndex);
        
        // 计算均匀分布的检查点
        int checkInterval = calculateCheckInterval(safeEnd - safeStart, cfg.maxStructuresPerRoad());
        
        for (int i = safeStart; i < safeEnd; i++) {
            // 只在特定间隔检查放置
            if ((i - safeStart) % checkInterval != 0) {
                continue;
            }
            
            // 已达到最大数量，停止
            if (ctx.isMaxReached(cfg.maxStructuresPerRoad())) {
                break;
            }
            
            BlockPos middle = middlePositions.get(i);
            BlockPos prev = middlePositions.get(i - 2);
            BlockPos next = middlePositions.get(i + 2);
            
            tryPlace(world, server, middle, prev, next, roadWidth, roadLength, ctx, random, cfg);
        }
    }
    
    /**
     * 计算检查间隔，使结构在道路上均匀分布
     */
    private static int calculateCheckInterval(int totalSegments, int maxStructures) {
        if (maxStructures <= 0 || totalSegments <= 0) {
            return Integer.MAX_VALUE;
        }
        // 为了放置 N 个结构，将道路分成 N+1 段，在每段中间检查
        return Math.max(1, totalSegments / (maxStructures + 1));
    }
    
    /**
     * 计算结构的旋转角度
     * 
     * @param dirX      道路方向 X 分量
     * @param dirZ      道路方向 Z 分量
     * @param leftSide  是否在左侧
     * @param faceRoad  是否需要面向道路
     * @return Minecraft 旋转枚举值
     */
    private static Rotation calculateRotation(double dirX, double dirZ, boolean leftSide, boolean faceRoad) {
        if (!faceRoad) {
            // 不需要特定朝向，返回随机旋转或默认
            return Rotation.NONE;
        }
        
        // 计算应该面向的方向
        // 如果在左侧，应该面向右（即道路方向的反方向的正交方向）
        // 简化处理：根据道路主方向决定旋转
        
        // 判断道路主要朝向
        double absX = Math.abs(dirX);
        double absZ = Math.abs(dirZ);
        
        if (absX > absZ) {
            // 道路主要是东西向
            if (leftSide) {
                // 在北侧，面向南
                return dirX > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
            } else {
                // 在南侧，面向北
                return dirX > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
            }
        } else {
            // 道路主要是南北向
            if (leftSide) {
                // 在西侧，面向东
                return dirZ > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
            } else {
                // 在东侧，面向西
                return dirZ > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
            }
        }
    }
    
    /**
     * 生成 5 点采样偏移（中心 + 四角）
     */
    private static int[][] getSampleOffsets(int halfX, int halfZ) {
        return new int[][] {
            {0, 0},
            {-halfX, -halfZ},
            {halfX, -halfZ},
            {-halfX, halfZ},
            {halfX, halfZ}
        };
    }
    
    /**
     * 采样结构底部区域的地面高度
     * @return [0]=最高点, [1]=最低点
     */
    private static int[] sampleGroundHeights(WorldGenLevel world, int centerX, int centerZ, int halfX, int halfZ) {
        int maxY = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        
        for (int[] offset : getSampleOffsets(halfX, halfZ)) {
            int x = centerX + offset[0];
            int z = centerZ + offset[1];
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            maxY = Math.max(maxY, y);
            minY = Math.min(minY, y);
        }
        
        return new int[]{maxY, minY};
    }
    
    /**
     * 检查放置位置是否在水上
     */
    private static boolean isOnWater(WorldGenLevel world, int centerX, int centerY, int centerZ, int halfX, int halfZ) {
        for (int[] offset : getSampleOffsets(halfX, halfZ)) {
            int x = centerX + offset[0];
            int z = centerZ + offset[1];
            // 检查放置高度及其下方是否有水
            for (int dy = 0; dy >= -2; dy--) {
                BlockState state = world.getBlockState(new BlockPos(x, centerY + dy, z));
                if (state.is(Blocks.WATER) || state.getFluidState().isSource()) {
                    return true;
                }
            }
        }
        return false;
    }
}

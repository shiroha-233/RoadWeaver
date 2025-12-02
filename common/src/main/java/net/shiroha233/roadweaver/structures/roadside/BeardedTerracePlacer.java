package net.shiroha233.roadweaver.structures.roadside;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 基于原版 Beardifier 算法的地形托盘生成器
 * 
 * 原理说明：
 * 原版 Minecraft 使用 Beardifier 在世界生成时通过密度函数调整结构周围的地形。
 * 它使用高斯衰减函数 Math.pow(E, -distance²/16) 来平滑过渡。
 * 
 * 本类将这一算法适配为后处理版本，在 Feature 阶段使用：
 * - 计算结构包围盒
 * - 使用原版的高斯衰减公式计算每个位置的目标高度
 * - 填充或清除方块以形成平滑的托盘
 * 
 * TerrainAdjustment 模式说明（原版）：
 * - NONE: 不调整地形
 * - BURY: 掩埋模式，适合地下结构
 * - BEARD_THIN: 薄胡须，最小地形调整
 * - BEARD_BOX: 盒状胡须，更激进的地形平整
 * 
 * 本实现采用 BEARD_THIN 风格，适合小型地表结构。
 * 
 * 支持两种使用场景：
 * - WorldGenLevel：在 Feature 阶段使用（路边结构）
 * - LevelAccessor/ServerLevel：在运行时使用（初始小屋）
 */
public final class BeardedTerracePlacer {
    private BeardedTerracePlacer() {}
    
    // 外圈过渡半径（碗状边缘的宽度）- 小型结构用
    private static final int TRANSITION_RADIUS = 4;
    // 最小衰减阈值（低于此值不修改地形）
    private static final double MIN_CONTRIBUTION = 0.05;
    // 小坑填补：在内环区域，向下检查并填补的最大深度
    private static final int PIT_FILL_DEPTH = 3;
    // 挖掘允许的最大深度（防止挖穿整座山）
    private static final int MAX_CUT_DEPTH = 6;
    
    /**
     * 为结构生成地形托盘
     * 
     * @param world    世界
     * @param anchor   结构锚点（左下角）
     * @param size     结构尺寸
     * @param random   随机源
     */
    public static void buildTerrace(WorldGenLevel world, BlockPos anchor, Vec3i size, RandomSource random) {
        // 计算结构中心
        int centerX = anchor.getX() + size.getX() / 2;
        int centerZ = anchor.getZ() + size.getZ() / 2;
        
        // 目标地面高度 = 锚点 Y - 1
        // 因为结构不带底座，结构的最底层方块从 anchor.getY() 开始
        // 所以地面应该在 anchor.getY() - 1
        int targetY = anchor.getY() - 1;
        
        // 计算内环半径（结构占用区域）和外环半径（过渡区域）
        // 使用圆形而非方形，让边缘更自然
        double innerRadius = Math.max(size.getX(), size.getZ()) / 2.0 + 0.5;
        double outerRadius = innerRadius + TRANSITION_RADIUS;
        
        // 遍历影响区域（圆形）
        int searchRadius = (int) Math.ceil(outerRadius) + 1;
        for (int x = centerX - searchRadius; x <= centerX + searchRadius; x++) {
            for (int z = centerZ - searchRadius; z <= centerZ + searchRadius; z++) {
                // 计算到中心的欧几里得距离（圆形）
                double dx = x - centerX;
                double dz = z - centerZ;
                double dist = Math.sqrt(dx * dx + dz * dz);
                
                // 超出外环，跳过
                if (dist > outerRadius) {
                    continue;
                }
                
                // 获取当前地表高度
                int groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                
                // 在内环（结构区域内）：双向适配 + 小坑填补
                if (dist <= innerRadius) {
                    if (groundY < targetY) {
                        // 地形低于目标：向上填充
                        fillColumnUp(world, x, z, groundY, targetY, random);
                    } else if (groundY > targetY) {
                        // 地形高于目标：向下挖掘（削平）
                        int cutDepth = groundY - targetY;
                        if (cutDepth <= MAX_CUT_DEPTH) {
                            clearColumnDown(world, x, z, targetY + 1, groundY);
                        }
                    }
                    // 小坑填补：检查 targetY 以下是否有空洞，防止浮空
                    fillPitBelow(world, x, z, targetY, random);
                    continue;
                }
                
                // 在过渡环：使用 smoothstep 平滑过渡（碗状效果）
                double t = (dist - innerRadius) / (outerRadius - innerRadius);  // 0~1
                double smooth = smoothstep(t);  // 平滑插值
                double contribution = 1.0 - smooth;  // 内部=1，外部=0
                
                if (contribution < MIN_CONTRIBUTION) {
                    continue;
                }
                
                // 计算目标高度（碗状：内高外低的平滑过渡）
                int finalY = (int) Math.round(targetY * contribution + groundY * (1.0 - contribution));
                
                // 双向适配：填充或挖掘
                if (finalY > groundY) {
                    fillColumnUp(world, x, z, groundY, finalY, random);
                } else if (finalY < groundY) {
                    int cutDepth = groundY - finalY;
                    if (cutDepth <= MAX_CUT_DEPTH) {
                        clearColumnDown(world, x, z, finalY + 1, groundY);
                    }
                }
            }
        }
        
        // 表层修复：将暴露在空气中的泥土替换为草方块
        // 这可以修复托盘侧面露出泥土的问题
        fixExposedDirt(world, centerX, centerZ, searchRadius, targetY);
    }
    
    /**
     * 以指定中心点生成地形托盘（WorldGenLevel 版本，用于路边结构）
     * 
     * @param world       世界
     * @param centerX     中心点 X
     * @param centerZ     中心点 Z
     * @param targetY     目标地面高度
     * @param innerRadius 内环半径
     * @param outerRadius 外环半径
     * @param random      随机源
     */
    public static void buildTerraceByCenter(WorldGenLevel world, int centerX, int centerZ, int targetY,
                                             int innerRadius, int outerRadius, RandomSource random) {
        int searchRadius = outerRadius + 2;
        
        for (int x = centerX - searchRadius; x <= centerX + searchRadius; x++) {
            for (int z = centerZ - searchRadius; z <= centerZ + searchRadius; z++) {
                double dx = x - centerX;
                double dz = z - centerZ;
                double dist = Math.sqrt(dx * dx + dz * dz);
                
                if (dist > outerRadius) {
                    continue;
                }
                
                int groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                
                // 内环：双向适配 + 小坑填补
                if (dist <= innerRadius) {
                    if (groundY < targetY) {
                        fillColumnUp(world, x, z, groundY, targetY, random);
                    } else if (groundY > targetY) {
                        int cutDepth = groundY - targetY;
                        if (cutDepth <= MAX_CUT_DEPTH) {
                            clearColumnDown(world, x, z, targetY + 1, groundY);
                        }
                    }
                    fillPitBelow(world, x, z, targetY, random);
                    continue;
                }
                
                // 过渡环：smoothstep 平滑
                double t = (dist - innerRadius) / (outerRadius - innerRadius);
                double smooth = smoothstep(t);
                double contribution = 1.0 - smooth;
                
                if (contribution < MIN_CONTRIBUTION) {
                    continue;
                }
                
                int finalY = (int) Math.round(targetY * contribution + groundY * (1.0 - contribution));
                
                if (finalY > groundY) {
                    fillColumnUp(world, x, z, groundY, finalY, random);
                } else if (finalY < groundY) {
                    int cutDepth = groundY - finalY;
                    if (cutDepth <= MAX_CUT_DEPTH) {
                        clearColumnDown(world, x, z, finalY + 1, groundY);
                    }
                }
            }
        }
        
        fixExposedDirt(world, centerX, centerZ, searchRadius, targetY);
    }
    
    /**
     * 为大型结构生成地形托盘（支持 ServerLevel，用于初始小屋等）
     * 
     * 与小型结构版本的区别：
     * - 过渡半径更大（LARGE_TRANSITION_RADIUS）
     * - 不需要锚点 Y 减 1（大型结构通常有底座）
     * 
     * @param level     世界（ServerLevel 或其他 LevelAccessor）
     * @param centerX   结构中心 X
     * @param centerZ   结构中心 Z
     * @param targetY   目标地面高度
     * @param innerRadius 内环半径（结构占用区域）
     * @param outerRadius 外环半径（过渡区域）
     * @param random    随机源
     */
    public static void buildTerraceForLargeStructure(LevelAccessor level, 
                                                      int centerX, int centerZ, int targetY,
                                                      int innerRadius, int outerRadius,
                                                      RandomSource random) {
        // 遍历影响区域（圆形）
        int searchRadius = outerRadius + 2;
        for (int x = centerX - searchRadius; x <= centerX + searchRadius; x++) {
            for (int z = centerZ - searchRadius; z <= centerZ + searchRadius; z++) {
                // 计算到中心的欧几里得距离（圆形）
                double dx = x - centerX;
                double dz = z - centerZ;
                double dist = Math.sqrt(dx * dx + dz * dz);
                
                // 超出外环，跳过
                if (dist > outerRadius) {
                    continue;
                }
                
                // 获取当前地表高度
                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                
                // 在内环（结构区域内）：双向适配 + 小坑填补
                if (dist <= innerRadius) {
                    if (groundY < targetY) {
                        fillColumnUpGeneric(level, x, z, groundY, targetY, random);
                    } else if (groundY > targetY) {
                        int cutDepth = groundY - targetY;
                        if (cutDepth <= MAX_CUT_DEPTH) {
                            clearColumnDownGeneric(level, x, z, targetY + 1, groundY);
                        }
                    }
                    fillPitBelowGeneric(level, x, z, targetY, random);
                    continue;
                }
                
                // 在过渡环：使用 smoothstep 平滑过渡（碗状效果）
                double t = (dist - innerRadius) / (outerRadius - innerRadius);
                double smooth = smoothstep(t);
                double contribution = 1.0 - smooth;
                
                if (contribution < MIN_CONTRIBUTION) {
                    continue;
                }
                
                // 计算目标高度
                int finalY = (int) Math.round(targetY * contribution + groundY * (1.0 - contribution));
                
                // 双向适配：填充或挖掘
                if (finalY > groundY) {
                    fillColumnUpGeneric(level, x, z, groundY, finalY, random);
                } else if (finalY < groundY) {
                    int cutDepth = groundY - finalY;
                    if (cutDepth <= MAX_CUT_DEPTH) {
                        clearColumnDownGeneric(level, x, z, finalY + 1, groundY);
                    }
                }
            }
        }
        
        // 表层修复
        fixExposedDirtGeneric(level, centerX, centerZ, searchRadius, targetY);
    }
    
    /**
     * 通用版本：向上填充地形列（支持 LevelAccessor）
     */
    private static void fillColumnUpGeneric(LevelAccessor level, int x, int z, int groundY, int targetY, RandomSource random) {
        BlockState surfaceState = level.getBlockState(new BlockPos(x, groundY, z));
        BlockState fillState = getSuitableFillState(surfaceState);
        BlockState topState = getSuitableTopState(surfaceState);
        
        for (int y = groundY + 1; y <= targetY; y++) {
            BlockState state = (y == targetY) ? topState : fillState;
            level.setBlock(new BlockPos(x, y, z), state, 3);  // flags=3 用于 ServerLevel
        }
    }
    
    /**
     * 通用版本：修复暴露的泥土（支持 LevelAccessor）
     */
    private static void fixExposedDirtGeneric(LevelAccessor level, int centerX, int centerZ, int radius, int baseY) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int y = baseY; y >= baseY - 5; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    
                    if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)) {
                        if (isExposedToAirGeneric(level, pos)) {
                            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                        }
                    }
                    if (state.is(Blocks.SNOW_BLOCK)) {
                        if (isExposedToAirGeneric(level, pos)) {
                            level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 通用版本：检查方块是否暴露在空气中
     */
    private static boolean isExposedToAirGeneric(LevelAccessor level, BlockPos pos) {
        if (level.getBlockState(pos.above()).isAir()) {
            return true;
        }
        return level.getBlockState(pos.north()).isAir() ||
               level.getBlockState(pos.south()).isAir() ||
               level.getBlockState(pos.east()).isAir() ||
               level.getBlockState(pos.west()).isAir();
    }
    
    /**
     * 修复暴露在空气中的泥土方块
     * 遍历托盘区域，将所有暴露的泥土替换为草方块
     */
    private static void fixExposedDirt(WorldGenLevel world, int centerX, int centerZ, int radius, int baseY) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                // 检查从 baseY 向下几格
                for (int y = baseY; y >= baseY - 5; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    
                    // 如果是泥土或类似方块，检查是否暴露
                    if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)) {
                        // 检查上方和四个侧面是否有空气
                        if (isExposedToAir(world, pos)) {
                            world.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                        }
                    }
                    // 处理雪地群系暴露的泥土
                    if (state.is(Blocks.SNOW_BLOCK)) {
                        if (isExposedToAir(world, pos)) {
                            world.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 检查方块是否暴露在空气中（上方或侧面有空气）
     */
    private static boolean isExposedToAir(WorldGenLevel world, BlockPos pos) {
        // 检查上方
        if (world.getBlockState(pos.above()).isAir()) {
            return true;
        }
        // 检查四个侧面
        return world.getBlockState(pos.north()).isAir() ||
               world.getBlockState(pos.south()).isAir() ||
               world.getBlockState(pos.east()).isAir() ||
               world.getBlockState(pos.west()).isAir();
    }
    
    /**
     * Smoothstep 插值函数：3t² - 2t³
     * 让过渡曲线在起点和终点的斜率为 0，产生平滑的碗状边缘
     */
    private static double smoothstep(double t) {
        t = Mth.clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
    
    /**
     * 向下挖掘地形列（削平高于目标的地形）
     */
    private static void clearColumnDown(WorldGenLevel world, int x, int z, int fromY, int toY) {
        for (int y = fromY; y <= toY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            // 只清除自然方块，不破坏人工结构
            if (isNaturalBlock(state)) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
    
    /**
     * 通用版本：向下挖掘地形列
     */
    private static void clearColumnDownGeneric(LevelAccessor level, int x, int z, int fromY, int toY) {
        for (int y = fromY; y <= toY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (isNaturalBlock(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
    
    /**
     * 填补目标高度以下的小坑（防止结构浮空）
     */
    private static void fillPitBelow(WorldGenLevel world, int x, int z, int targetY, RandomSource random) {
        // 获取表层材质用于填充
        BlockState surfaceState = world.getBlockState(new BlockPos(x, targetY, z));
        BlockState fillState = getSuitableFillState(surfaceState);
        
        // 从 targetY 向下检查 PIT_FILL_DEPTH 格
        for (int y = targetY; y > targetY - PIT_FILL_DEPTH; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            // 如果是空气或可替换方块（水、草等），填充
            if (state.isAir() || state.canBeReplaced()) {
                world.setBlock(pos, fillState, 2);
            } else {
                // 遇到实心方块，停止向下检查
                break;
            }
        }
    }
    
    /**
     * 通用版本：填补目标高度以下的小坑
     */
    private static void fillPitBelowGeneric(LevelAccessor level, int x, int z, int targetY, RandomSource random) {
        BlockState surfaceState = level.getBlockState(new BlockPos(x, targetY, z));
        BlockState fillState = getSuitableFillState(surfaceState);
        
        for (int y = targetY; y > targetY - PIT_FILL_DEPTH; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) {
                level.setBlock(pos, fillState, 3);
            } else {
                break;
            }
        }
    }
    
    /**
     * 判断是否为自然生成的方块（可被挖掘）
     */
    private static boolean isNaturalBlock(BlockState state) {
        // 自然方块：泥土、石头、沙子、砂砾、草方块等
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) ||
               state.is(Blocks.STONE) || state.is(Blocks.SAND) ||
               state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) ||
               state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) ||
               state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) ||
               state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) ||
               state.is(Blocks.GRANITE) || state.is(Blocks.DEEPSLATE) ||
               state.is(Blocks.TUFF) || state.is(Blocks.CALCITE) ||
               state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW) ||
               state.is(Blocks.CLAY) || state.is(Blocks.MUD) ||
               state.is(Blocks.PACKED_MUD) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }
    
    /**
     * 向上填充地形列（用于在结构内部提供底座）
     */
    private static void fillColumnUp(WorldGenLevel world, int x, int z, int groundY, int targetY, RandomSource random) {
        // 获取表层方块类型
        BlockState surfaceState = world.getBlockState(new BlockPos(x, groundY, z));
        BlockState fillState = getSuitableFillState(surfaceState);
        BlockState topState = getSuitableTopState(surfaceState);
        
        // 从原地表向上填充
        for (int y = groundY + 1; y <= targetY; y++) {
            BlockState state = (y == targetY) ? topState : fillState;
            world.setBlock(new BlockPos(x, y, z), state, 2);
        }
    }
    
    /**
     * 根据原始表层方块获取合适的填充方块
     */
    private static BlockState getSuitableFillState(BlockState surface) {
        // 如果是草方块或泥土变种，用泥土填充
        if (surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.DIRT) || 
            surface.is(Blocks.PODZOL) || surface.is(Blocks.MYCELIUM)) {
            return Blocks.DIRT.defaultBlockState();
        }
        // 如果是沙子，用沙子填充
        if (surface.is(Blocks.SAND)) {
            return Blocks.SAND.defaultBlockState();
        }
        // 如果是红沙，用红沙填充
        if (surface.is(Blocks.RED_SAND)) {
            return Blocks.RED_SAND.defaultBlockState();
        }
        // 如果是砂砾，用砂砾填充
        if (surface.is(Blocks.GRAVEL)) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        // 如果是石头类，用石头填充
        if (surface.is(Blocks.STONE) || surface.is(Blocks.ANDESITE) || 
            surface.is(Blocks.DIORITE) || surface.is(Blocks.GRANITE)) {
            return Blocks.STONE.defaultBlockState();
        }
        // 默认用泥土
        return Blocks.DIRT.defaultBlockState();
    }
    
    /**
     * 根据原始表层方块获取合适的顶层方块
     */
    private static BlockState getSuitableTopState(BlockState surface) {
        // 保持原始表层类型
        if (surface.is(Blocks.GRASS_BLOCK)) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (surface.is(Blocks.PODZOL)) {
            return Blocks.PODZOL.defaultBlockState();
        }
        if (surface.is(Blocks.MYCELIUM)) {
            return Blocks.MYCELIUM.defaultBlockState();
        }
        if (surface.is(Blocks.SAND)) {
            return Blocks.SAND.defaultBlockState();
        }
        if (surface.is(Blocks.RED_SAND)) {
            return Blocks.RED_SAND.defaultBlockState();
        }
        if (surface.is(Blocks.GRAVEL)) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        // 默认用草方块
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }
}

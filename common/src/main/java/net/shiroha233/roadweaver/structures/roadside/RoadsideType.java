package net.shiroha233.roadweaver.structures.roadside;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

/**
 * 路边结构类型枚举
 * 
 * 添加新结构时，只需在此处添加枚举值并指定放置规则。
 * 
 * 字段说明：
 * - templateId: 结构模板路径
 * - sizeHint: 结构尺寸提示 (X, Y, Z)
 * - weight: 选择权重（同条件下权重越高越容易被选中）
 * - faceRoad: 是否需要面向道路
 * - scale: 结构规模（决定距离道路的偏移）
 * - placementRule: 放置规则（群系、道路长度限制）
 * 
 * @see StructureScale 结构规模定义
 * @see RoadsidePlacementRule 放置规则定义
 */
public enum RoadsideType {
    
    // ==================== 小型结构 ====================
    
    /** 长椅 - 温带群系，通用 */
    BENCH("roadside/roadside_bench", 3, 2, 2, 10, true, StructureScale.SMALL,
          RoadsidePlacementRule.TEMPERATE),
    
    /** 营火 - 所有群系，通用 */
    CAMPFIRE("roadside/small_campfire", 3, 3, 3, 8, false, StructureScale.SMALL,
             RoadsidePlacementRule.UNIVERSAL),
    
    // ==================== 中型结构 ====================
    
    /** 樱花咖啡屋 - 仅樱花林，需要长道路 */
    SAKURA_COFFEE_HOUSE("roadside/sakura_coffee_house", 12, 10, 12, 5, true, StructureScale.MEDIUM,
                        RoadsidePlacementRule.builder()
                                .allow(BiomeCategory.CHERRY_GROVE)
                                .minRoadLength(50)
                                .build());
    
    // ==================== 字段 ====================
    
    private final ResourceLocation templateId;
    private final Vec3i sizeHint;
    private final int weight;
    private final boolean faceRoad;
    private final StructureScale scale;
    private final RoadsidePlacementRule placementRule;
    
    RoadsideType(String path, int sizeX, int sizeY, int sizeZ, int weight, 
                 boolean faceRoad, StructureScale scale, RoadsidePlacementRule placementRule) {
        this.templateId = new ResourceLocation("roadweaver", path);
        this.sizeHint = new Vec3i(sizeX, sizeY, sizeZ);
        this.weight = weight;
        this.faceRoad = faceRoad;
        this.scale = scale;
        this.placementRule = placementRule;
    }
    
    /**
     * 获取结构模板的 ResourceLocation
     * 路径格式: roadweaver:roadside/xxx
     * 对应文件: data/roadweaver/structures/roadside/xxx.nbt
     */
    public ResourceLocation templateId() {
        return templateId;
    }
    
    /**
     * 获取结构的大致尺寸（X, Y, Z）
     * 用于碰撞检测和确定放置位置的偏移
     */
    public Vec3i sizeHint() {
        return sizeHint;
    }
    
    /**
     * 获取随机选择权重
     * 权重越高，被选中的概率越大
     */
    public int weight() {
        return weight;
    }
    
    /**
     * 是否需要面向道路放置
     */
    public boolean faceRoad() {
        return faceRoad;
    }
    
    /**
     * 获取结构规模
     */
    public StructureScale scale() {
        return scale;
    }
    
    /**
     * 获取放置规则
     */
    public RoadsidePlacementRule placementRule() {
        return placementRule;
    }
    
    // ==================== 静态方法 ====================
    
    /**
     * 根据权重随机选择结构类型（旧版兼容，不推荐使用）
     */
    @Deprecated
    public static RoadsideType chooseWeighted(net.minecraft.util.RandomSource random) {
        int total = 0;
        for (RoadsideType t : values()) total += t.weight;
        
        int roll = random.nextInt(total);
        int sum = 0;
        for (RoadsideType t : values()) {
            sum += t.weight;
            if (roll < sum) return t;
        }
        return values()[0];
    }
    
    /**
     * 根据群系和道路长度过滤后，按权重随机选择结构类型
     * 
     * @param random       随机源
     * @param biome        当前群系分类
     * @param roadLength   道路长度（路段数）
     * @return 符合条件的结构类型，如果没有符合条件的返回 null
     */
    public static RoadsideType chooseWeightedFiltered(
            net.minecraft.util.RandomSource random,
            BiomeCategory biome,
            int roadLength) {
        
        // 第一遍：收集符合条件的结构及其权重
        java.util.List<RoadsideType> candidates = new java.util.ArrayList<>();
        int totalWeight = 0;
        
        for (RoadsideType type : values()) {
            RoadsidePlacementRule rule = type.placementRule;
            
            // 检查群系
            if (!rule.isBiomeAllowed(biome)) {
                continue;
            }
            
            // 检查道路长度
            if (!rule.isRoadLongEnough(roadLength)) {
                continue;
            }
            
            candidates.add(type);
            totalWeight += type.weight;
        }
        
        // 没有符合条件的结构
        if (candidates.isEmpty() || totalWeight <= 0) {
            return null;
        }
        
        // 按权重随机选择
        int roll = random.nextInt(totalWeight);
        int sum = 0;
        for (RoadsideType type : candidates) {
            sum += type.weight;
            if (roll < sum) {
                return type;
            }
        }
        
        // 理论上不会到达这里
        return candidates.get(0);
    }
}

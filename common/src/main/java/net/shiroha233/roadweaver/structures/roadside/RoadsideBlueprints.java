package net.shiroha233.roadweaver.structures.roadside;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.structures.StructureSystem;
import net.shiroha233.roadweaver.structures.api.BlendProfile;
import net.shiroha233.roadweaver.structures.api.SpawnRule;
import net.shiroha233.roadweaver.structures.api.StructureBlueprint;
import net.shiroha233.roadweaver.structures.api.StructureConnector;
import net.shiroha233.roadweaver.structures.api.StructureVariant;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 路边结构蓝图注册与管理
 * 
 * 职责：
 * 1. 为每种 RoadsideType 创建对应的 StructureBlueprint
 * 2. 将蓝图注册到 StructureSystem
 * 3. 提供根据类型获取蓝图的方法
 * 
 * 设计说明：
 * - 路边结构体积较小，不需要复杂的地形融合（BlendProfile 使用 NONE）
 * - SpawnRule 主要用于控制最小间距，防止结构过于密集
 * - 蓝图在模组初始化时统一注册
 */
public final class RoadsideBlueprints {
    private RoadsideBlueprints() {}
    
    // 缓存每种类型对应的蓝图，避免重复创建
    private static final Map<RoadsideType, StructureBlueprint> CACHE = new EnumMap<>(RoadsideType.class);
    
    // 是否已初始化
    private static volatile boolean initialized = false;
    
    /**
     * 初始化并注册所有路边结构蓝图
     * 采用懒加载模式，首次获取蓝图时自动调用
     */
    public static synchronized void registerAll() {
        if (initialized) {
            return;
        }
        for (RoadsideType type : RoadsideType.values()) {
            StructureBlueprint bp = createBlueprint(type);
            CACHE.put(type, bp);
            StructureSystem.registerBlueprint(bp);
        }
        initialized = true;
    }
    
    /**
     * 根据路边结构类型获取对应蓝图
     * 首次调用时会自动初始化所有蓝图（懒加载）
     * 
     * @param type 路边结构类型
     * @return 对应的蓝图
     */
    public static StructureBlueprint get(RoadsideType type) {
        if (!initialized) {
            registerAll();
        }
        return CACHE.get(type);
    }
    
    /**
     * 获取指定类型的蓝图 ID
     */
    public static ResourceLocation blueprintId(RoadsideType type) {
        return new ResourceLocation("roadweaver", "roadside_" + type.name().toLowerCase());
    }
    
    /**
     * 为指定类型创建蓝图
     * 
     * @param type 路边结构类型
     * @return 创建的蓝图对象
     */
    private static StructureBlueprint createBlueprint(RoadsideType type) {
        ResourceLocation id = blueprintId(type);
        ResourceLocation templateId = type.templateId();
        Vec3i sizeHint = type.sizeHint();
        
        // 创建结构变体列表（目前每种类型只有一个变体）
        List<StructureVariant> variants = Collections.singletonList(
                new StructureVariant(templateId, type.weight(), true)  // 允许旋转
        );
        
        // 路边结构不需要连接器
        List<StructureConnector> connectors = Collections.emptyList();
        
        // 路边结构体积小，不需要地形融合（传 null 表示跳过融合步骤）
        BlendProfile blend = null;
        
        // 生成规则：主要控制最小间距
        SpawnRule rule = createSpawnRule(type);
        
        return new StructureBlueprint(id, variants, connectors, sizeHint, blend, rule);
    }
    
    /**
     * 创建生成规则
     * 
     * SpawnRule 参数说明：
     * - spacing: 同类型结构之间的最小间距（格）
     * - separation: 与任意其他结构的最小间距（格）
     * - minY/maxY: 生成高度范围
     * - maxSlope: 最大坡度（0=不限制）
     * - avoidRadius: 避让半径（0=不避让）
     */
    private static SpawnRule createSpawnRule(RoadsideType type) {
        // 使用结构规模的默认间距（添加新结构时无需修改此处）
        StructureScale scale = type.scale();
        int spacing = scale.defaultSpacing();
        int separation = scale.defaultSeparation();
        
        return new SpawnRule(
                Collections.emptySet(),  // dimensionAllow: 允许的维度（空=全部）
                Collections.emptySet(),  // biomeAllowTags: 允许的群系（空=全部）
                Collections.emptySet(),  // biomeDenyTags: 排除的群系
                spacing,                 // spacing: 同类型最小间距
                separation,              // separation: 任意结构最小间距
                -64,                     // minY: 最小 Y（主世界）
                320,                     // maxY: 最大 Y
                0,                       // maxSlope: 最大坡度（0=不限制）
                0                        // avoidRadius: 避让半径
        );
    }
    
    /**
     * 清除蓝图缓存（用于热重载或服务器关闭）
     */
    public static synchronized void clearCache() {
        CACHE.clear();
        initialized = false;
    }
}

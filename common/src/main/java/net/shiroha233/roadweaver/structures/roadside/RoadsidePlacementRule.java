package net.shiroha233.roadweaver.structures.roadside;

import java.util.EnumSet;
import java.util.Set;

/**
 * 路边结构放置规则（硬编码）
 * 
 * 定义单个结构类型的放置条件：
 * - 允许的群系分类
 * - 最小道路长度要求
 * 
 * 设计原则：规则不可配置，保证游戏体验的一致性。
 * 可配置的参数（如距离、数量上限）由 ModConfig 管理。
 */
public final class RoadsidePlacementRule {
    
    /** 允许放置的群系分类（空集合=全部允许） */
    private final Set<BiomeCategory> allowedBiomes;
    
    /** 道路最小长度（路段数），只有超过此长度才考虑放置 */
    private final int minRoadLength;
    
    /** 私有构造，使用 Builder */
    private RoadsidePlacementRule(Set<BiomeCategory> allowedBiomes, int minRoadLength) {
        this.allowedBiomes = allowedBiomes.isEmpty() 
                ? EnumSet.allOf(BiomeCategory.class) 
                : EnumSet.copyOf(allowedBiomes);
        this.minRoadLength = Math.max(0, minRoadLength);
    }
    
    /**
     * 检查群系是否允许放置
     */
    public boolean isBiomeAllowed(BiomeCategory category) {
        return allowedBiomes.contains(category);
    }
    
    /**
     * 检查道路长度是否满足要求
     */
    public boolean isRoadLongEnough(int roadLength) {
        return roadLength >= minRoadLength;
    }
    
    /**
     * 获取允许的群系集合
     */
    public Set<BiomeCategory> allowedBiomes() {
        return EnumSet.copyOf(allowedBiomes);
    }
    
    /**
     * 获取最小道路长度
     */
    public int minRoadLength() {
        return minRoadLength;
    }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final Set<BiomeCategory> allowedBiomes = EnumSet.noneOf(BiomeCategory.class);
        private int minRoadLength = 0;
        
        private Builder() {}
        
        /** 添加允许的群系分类 */
        public Builder allow(BiomeCategory... categories) {
            for (BiomeCategory cat : categories) {
                allowedBiomes.add(cat);
            }
            return this;
        }
        
        /** 设置允许所有群系 */
        public Builder allowAll() {
            allowedBiomes.addAll(EnumSet.allOf(BiomeCategory.class));
            return this;
        }
        
        /** 排除指定群系（先 allowAll 再排除） */
        public Builder exclude(BiomeCategory... categories) {
            for (BiomeCategory cat : categories) {
                allowedBiomes.remove(cat);
            }
            return this;
        }
        
        /** 设置最小道路长度 */
        public Builder minRoadLength(int length) {
            this.minRoadLength = length;
            return this;
        }
        
        public RoadsidePlacementRule build() {
            return new RoadsidePlacementRule(allowedBiomes, minRoadLength);
        }
    }
    
    // ==================== 预定义规则 ====================
    
    /** 通用规则：所有群系，无道路长度限制 */
    public static final RoadsidePlacementRule UNIVERSAL = builder()
            .allowAll()
            .minRoadLength(0)
            .build();
    
    /** 温带规则：平原、森林、针叶林、樱花林 */
    public static final RoadsidePlacementRule TEMPERATE = builder()
            .allow(BiomeCategory.PLAINS, BiomeCategory.FOREST, BiomeCategory.TAIGA, 
                   BiomeCategory.CHERRY_GROVE)
            .minRoadLength(0)
            .build();
    
    /** 寒带规则：雪地、针叶林 */
    public static final RoadsidePlacementRule COLD = builder()
            .allow(BiomeCategory.SNOWY, BiomeCategory.TAIGA)
            .minRoadLength(0)
            .build();
    
    /** 热带规则：沙漠、热带草原、恶地 */
    public static final RoadsidePlacementRule HOT = builder()
            .allow(BiomeCategory.DESERT, BiomeCategory.SAVANNA, BiomeCategory.BADLANDS)
            .minRoadLength(0)
            .build();
    
    /** 长距离规则：只在长道路上出现（100段以上） */
    public static final RoadsidePlacementRule LONG_ROAD_ONLY = builder()
            .allowAll()
            .minRoadLength(100)
            .build();
    
    /** 超长距离规则：只在超长道路上出现（200段以上） */
    public static final RoadsidePlacementRule VERY_LONG_ROAD_ONLY = builder()
            .allowAll()
            .minRoadLength(200)
            .build();
    
    /** 樱花林专属 */
    public static final RoadsidePlacementRule CHERRY_ONLY = builder()
            .allow(BiomeCategory.CHERRY_GROVE)
            .minRoadLength(0)
            .build();
    
    /** 森林专属 */
    public static final RoadsidePlacementRule FOREST_ONLY = builder()
            .allow(BiomeCategory.FOREST)
            .minRoadLength(0)
            .build();
}

package net.shiroha233.roadweaver.structures.roadside;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * 群系分类枚举
 * 
 * 用于将 Minecraft 群系分组，便于路边结构根据群系类型选择放置。
 * 每个分类对应一组群系 ID 或群系标签。
 */
public enum BiomeCategory {
    
    /** 平原类（平原、向日葵平原、草甸） */
    PLAINS,
    
    /** 森林类（森林、桦木森林、黑森林、繁花森林） */
    FOREST,
    
    /** 针叶林类（针叶林、云杉林、老生长针叶林） */
    TAIGA,
    
    /** 丛林类 */
    JUNGLE,
    
    /** 沙漠类 */
    DESERT,
    
    /** 热带草原/稀树草原 */
    SAVANNA,
    
    /** 恶地/荒原 */
    BADLANDS,
    
    /** 雪地/冰原 */
    SNOWY,
    
    /** 沼泽类 */
    SWAMP,
    
    /** 樱花树林 */
    CHERRY_GROVE,
    
    /** 蘑菇岛 */
    MUSHROOM,
    
    /** 山地/高原 */
    MOUNTAIN,
    
    /** 海滩/河岸 */
    BEACH,
    
    /** 其他/未分类（默认） */
    OTHER;
    
    // 群系 ID 常量（避免硬编码字符串）
    private static final ResourceLocation PLAINS_ID = new ResourceLocation("minecraft", "plains");
    private static final ResourceLocation SUNFLOWER_PLAINS_ID = new ResourceLocation("minecraft", "sunflower_plains");
    private static final ResourceLocation MEADOW_ID = new ResourceLocation("minecraft", "meadow");
    private static final ResourceLocation FOREST_ID = new ResourceLocation("minecraft", "forest");
    private static final ResourceLocation FLOWER_FOREST_ID = new ResourceLocation("minecraft", "flower_forest");
    private static final ResourceLocation BIRCH_FOREST_ID = new ResourceLocation("minecraft", "birch_forest");
    private static final ResourceLocation OLD_GROWTH_BIRCH_FOREST_ID = new ResourceLocation("minecraft", "old_growth_birch_forest");
    private static final ResourceLocation DARK_FOREST_ID = new ResourceLocation("minecraft", "dark_forest");
    private static final ResourceLocation TAIGA_ID = new ResourceLocation("minecraft", "taiga");
    private static final ResourceLocation OLD_GROWTH_PINE_TAIGA_ID = new ResourceLocation("minecraft", "old_growth_pine_taiga");
    private static final ResourceLocation OLD_GROWTH_SPRUCE_TAIGA_ID = new ResourceLocation("minecraft", "old_growth_spruce_taiga");
    private static final ResourceLocation SNOWY_TAIGA_ID = new ResourceLocation("minecraft", "snowy_taiga");
    private static final ResourceLocation JUNGLE_ID = new ResourceLocation("minecraft", "jungle");
    private static final ResourceLocation SPARSE_JUNGLE_ID = new ResourceLocation("minecraft", "sparse_jungle");
    private static final ResourceLocation BAMBOO_JUNGLE_ID = new ResourceLocation("minecraft", "bamboo_jungle");
    private static final ResourceLocation DESERT_ID = new ResourceLocation("minecraft", "desert");
    private static final ResourceLocation SAVANNA_ID = new ResourceLocation("minecraft", "savanna");
    private static final ResourceLocation SAVANNA_PLATEAU_ID = new ResourceLocation("minecraft", "savanna_plateau");
    private static final ResourceLocation WINDSWEPT_SAVANNA_ID = new ResourceLocation("minecraft", "windswept_savanna");
    private static final ResourceLocation BADLANDS_ID = new ResourceLocation("minecraft", "badlands");
    private static final ResourceLocation WOODED_BADLANDS_ID = new ResourceLocation("minecraft", "wooded_badlands");
    private static final ResourceLocation ERODED_BADLANDS_ID = new ResourceLocation("minecraft", "eroded_badlands");
    private static final ResourceLocation SNOWY_PLAINS_ID = new ResourceLocation("minecraft", "snowy_plains");
    private static final ResourceLocation ICE_SPIKES_ID = new ResourceLocation("minecraft", "ice_spikes");
    private static final ResourceLocation SNOWY_SLOPES_ID = new ResourceLocation("minecraft", "snowy_slopes");
    private static final ResourceLocation FROZEN_PEAKS_ID = new ResourceLocation("minecraft", "frozen_peaks");
    private static final ResourceLocation SWAMP_ID = new ResourceLocation("minecraft", "swamp");
    private static final ResourceLocation MANGROVE_SWAMP_ID = new ResourceLocation("minecraft", "mangrove_swamp");
    private static final ResourceLocation CHERRY_GROVE_ID = new ResourceLocation("minecraft", "cherry_grove");
    private static final ResourceLocation MUSHROOM_FIELDS_ID = new ResourceLocation("minecraft", "mushroom_fields");
    private static final ResourceLocation WINDSWEPT_HILLS_ID = new ResourceLocation("minecraft", "windswept_hills");
    private static final ResourceLocation WINDSWEPT_GRAVELLY_HILLS_ID = new ResourceLocation("minecraft", "windswept_gravelly_hills");
    private static final ResourceLocation WINDSWEPT_FOREST_ID = new ResourceLocation("minecraft", "windswept_forest");
    private static final ResourceLocation STONY_PEAKS_ID = new ResourceLocation("minecraft", "stony_peaks");
    private static final ResourceLocation JAGGED_PEAKS_ID = new ResourceLocation("minecraft", "jagged_peaks");
    private static final ResourceLocation BEACH_ID = new ResourceLocation("minecraft", "beach");
    private static final ResourceLocation SNOWY_BEACH_ID = new ResourceLocation("minecraft", "snowy_beach");
    private static final ResourceLocation STONY_SHORE_ID = new ResourceLocation("minecraft", "stony_shore");
    
    /**
     * 根据群系 Holder 判断其分类
     * 
     * @param biome 群系 Holder
     * @return 对应的分类
     */
    public static BiomeCategory fromBiome(Holder<Biome> biome) {
        if (biome == null) {
            return OTHER;
        }
        
        // 获取群系的 ResourceLocation
        ResourceLocation biomeId = biome.unwrapKey()
                .map(key -> key.location())
                .orElse(null);
        
        if (biomeId == null) {
            return OTHER;
        }
        
        // 根据群系 ID 判断分类
        return fromBiomeId(biomeId, biome);
    }
    
    /**
     * 根据群系 ID 判断分类
     */
    private static BiomeCategory fromBiomeId(ResourceLocation id, Holder<Biome> biome) {
        // 平原类
        if (id.equals(PLAINS_ID) || id.equals(SUNFLOWER_PLAINS_ID) || id.equals(MEADOW_ID)) {
            return PLAINS;
        }
        
        // 森林类
        if (id.equals(FOREST_ID) || id.equals(FLOWER_FOREST_ID) || 
            id.equals(BIRCH_FOREST_ID) || id.equals(OLD_GROWTH_BIRCH_FOREST_ID) ||
            id.equals(DARK_FOREST_ID)) {
            return FOREST;
        }
        
        // 针叶林类
        if (id.equals(TAIGA_ID) || id.equals(OLD_GROWTH_PINE_TAIGA_ID) || 
            id.equals(OLD_GROWTH_SPRUCE_TAIGA_ID) || id.equals(SNOWY_TAIGA_ID)) {
            return TAIGA;
        }
        
        // 丛林类
        if (id.equals(JUNGLE_ID) || id.equals(SPARSE_JUNGLE_ID) || id.equals(BAMBOO_JUNGLE_ID)) {
            return JUNGLE;
        }
        
        // 沙漠类
        if (id.equals(DESERT_ID)) {
            return DESERT;
        }
        
        // 热带草原类
        if (id.equals(SAVANNA_ID) || id.equals(SAVANNA_PLATEAU_ID) || id.equals(WINDSWEPT_SAVANNA_ID)) {
            return SAVANNA;
        }
        
        // 恶地类
        if (id.equals(BADLANDS_ID) || id.equals(WOODED_BADLANDS_ID) || id.equals(ERODED_BADLANDS_ID)) {
            return BADLANDS;
        }
        
        // 雪地类
        if (id.equals(SNOWY_PLAINS_ID) || id.equals(ICE_SPIKES_ID) || 
            id.equals(SNOWY_SLOPES_ID) || id.equals(FROZEN_PEAKS_ID)) {
            return SNOWY;
        }
        
        // 沼泽类
        if (id.equals(SWAMP_ID) || id.equals(MANGROVE_SWAMP_ID)) {
            return SWAMP;
        }
        
        // 樱花林
        if (id.equals(CHERRY_GROVE_ID)) {
            return CHERRY_GROVE;
        }
        
        // 蘑菇岛
        if (id.equals(MUSHROOM_FIELDS_ID)) {
            return MUSHROOM;
        }
        
        // 山地类
        if (id.equals(WINDSWEPT_HILLS_ID) || id.equals(WINDSWEPT_GRAVELLY_HILLS_ID) ||
            id.equals(WINDSWEPT_FOREST_ID) || id.equals(STONY_PEAKS_ID) || id.equals(JAGGED_PEAKS_ID)) {
            return MOUNTAIN;
        }
        
        // 海滩类
        if (id.equals(BEACH_ID) || id.equals(SNOWY_BEACH_ID) || id.equals(STONY_SHORE_ID)) {
            return BEACH;
        }
        
        // 使用标签作为后备检测
        if (biome.is(BiomeTags.IS_FOREST)) {
            return FOREST;
        }
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return TAIGA;
        }
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return JUNGLE;
        }
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            return BADLANDS;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN)) {
            return MOUNTAIN;
        }
        if (biome.is(BiomeTags.IS_BEACH)) {
            return BEACH;
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return SAVANNA;
        }
        
        return OTHER;
    }
}

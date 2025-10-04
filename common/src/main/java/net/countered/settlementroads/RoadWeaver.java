package net.countered.settlementroads;

import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.features.config.RoadFeatureRegistry;
import net.countered.settlementroads.platform.PlatformHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RoadWeaver主类 - 通用初始化逻辑
 */
public class RoadWeaver {
    
    public static final String MOD_ID = "roadweaver";
    public static final String MOD_NAME = "RoadWeaver";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    /**
     * 通用初始化方法，由平台特定的入口点调用
     */
    public static void init() {
        LOGGER.info("Initializing RoadWeaver...");
        
        // 注册数据附件（平台特定）
        PlatformHelper.registerWorldDataAttachments();
        
        // 初始化配置
        ModConfig.init(MOD_ID, ModConfig.class);
        
        // 注册世界生成特性
        RoadFeatureRegistry.registerFeatures();
        
        // 注册事件处理器（平台特定）
        PlatformHelper.registerEventHandlers();
        
        LOGGER.info("RoadWeaver initialized successfully!");
    }
    
    /**
     * 客户端初始化
     */
    public static void initClient() {
        LOGGER.info("Initializing RoadWeaver client...");
        
        // 注册客户端事件（平台特定）
        PlatformHelper.registerClientEventHandlers();
        
        LOGGER.info("RoadWeaver client initialized successfully!");
    }
    
    public static Logger getLogger() {
        return LOGGER;
    }
}

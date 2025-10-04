package net.countered.settlementroads.fabric;

import net.countered.settlementroads.RoadWeaver;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric平台入口点
 */
public class RoadWeaverFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {
        // 调用通用初始化
        RoadWeaver.init();
    }
}

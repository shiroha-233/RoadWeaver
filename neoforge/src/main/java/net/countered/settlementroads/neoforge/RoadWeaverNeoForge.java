package net.countered.settlementroads.neoforge;

import net.countered.settlementroads.RoadWeaver;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge平台入口点
 */
@Mod(RoadWeaver.MOD_ID)
public class RoadWeaverNeoForge {
    
    public RoadWeaverNeoForge() {
        // 调用通用初始化
        RoadWeaver.init();
        // 事件注册交由 @Mod.EventBusSubscriber 注解类处理
    }
}

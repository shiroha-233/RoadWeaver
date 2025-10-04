package net.countered.settlementroads.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * 平台抽象接口
 * 使用Architectury的ExpectPlatform注解来实现平台特定功能
 */
public class PlatformHelper {
    
    /**
     * 获取当前平台名称
     */
    @ExpectPlatform
    public static String getPlatformName() {
        throw new AssertionError();
    }
    
    /**
     * 检查是否为开发环境
     */
    @ExpectPlatform
    public static boolean isDevelopmentEnvironment() {
        throw new AssertionError();
    }
    
    /**
     * 获取配置目录
     */
    @ExpectPlatform
    public static Path getConfigPath() {
        throw new AssertionError();
    }
    
    /**
     * 注册世界数据附件（平台特定）
     */
    @ExpectPlatform
    public static void registerWorldDataAttachments() {
        throw new AssertionError();
    }
    
    /**
     * 注册事件处理器（平台特定）
     */
    @ExpectPlatform
    public static void registerEventHandlers() {
        throw new AssertionError();
    }
    
    /**
     * 注册客户端事件处理器（平台特定）
     */
    @ExpectPlatform
    public static void registerClientEventHandlers() {
        throw new AssertionError();
    }
    
    /**
     * 检查是否为物理客户端
     */
    @ExpectPlatform
    public static boolean isPhysicalClient() {
        throw new AssertionError();
    }
    
    /**
     * 获取当前服务器实例（仅客户端调用）
     */
    @ExpectPlatform
    public static MinecraftServer getServerInClient() {
        throw new AssertionError();
    }
}

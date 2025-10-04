package net.countered.settlementroads.platform.fabric;

import net.countered.settlementroads.fabric.events.FabricEventHandler;
import net.countered.settlementroads.fabric.persistence.FabricWorldDataAttachment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * Fabric平台的PlatformHelper实现
 * 用于Architectury的@ExpectPlatform机制
 */
public class PlatformHelperImpl {
    
    /**
     * 获取当前平台名称
     */
    public static String getPlatformName() {
        return "Fabric";
    }

    /**
     * 检查是否为开发环境
     */
    public static boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    /**
     * 获取配置目录
     */
    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /**
     * 注册世界数据附件（平台特定）
     */
    public static void registerWorldDataAttachments() {
        FabricWorldDataAttachment.register();
    }

    /**
     * 注册事件处理器（平台特定）
     */
    public static void registerEventHandlers() {
        FabricEventHandler.register();
    }

    /**
     * 注册客户端事件处理器（平台特定）
     */
    public static void registerClientEventHandlers() {
        FabricEventHandler.registerClient();
    }

    /**
     * 检查是否为物理客户端
     */
    public static boolean isPhysicalClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    /**
     * 获取当前服务器实例（仅客户端调用）
     */
    public static MinecraftServer getServerInClient() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return null;
        }
        return ClientOnly.get();
    }

    @Environment(EnvType.CLIENT)
    private static class ClientOnly {
        private static MinecraftServer get() {
            return net.minecraft.client.MinecraftClient.getInstance().getServer();
        }
    }
}
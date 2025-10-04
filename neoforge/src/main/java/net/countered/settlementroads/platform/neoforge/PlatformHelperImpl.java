package net.countered.settlementroads.platform.neoforge;

import net.countered.settlementroads.neoforge.platform.NeoForgePlatformHelper;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * NeoForge平台的PlatformHelper实现
 * 用于Architectury的@ExpectPlatform机制
 */
public class PlatformHelperImpl {
    
    public static String getPlatformName() {
        return NeoForgePlatformHelper.getPlatformName();
    }
    
    public static boolean isDevelopmentEnvironment() {
        return NeoForgePlatformHelper.isDevelopmentEnvironment();
    }
    
    public static Path getConfigPath() {
        return NeoForgePlatformHelper.getConfigPath();
    }
    
    public static void registerWorldDataAttachments() {
        NeoForgePlatformHelper.registerWorldDataAttachments();
    }
    
    public static void registerEventHandlers() {
        NeoForgePlatformHelper.registerEventHandlers();
    }
    
    public static void registerClientEventHandlers() {
        NeoForgePlatformHelper.registerClientEventHandlers();
    }
    
    public static boolean isPhysicalClient() {
        return NeoForgePlatformHelper.isPhysicalClient();
    }
    
    public static MinecraftServer getServerInClient() {
        return NeoForgePlatformHelper.getServerInClient();
    }
}

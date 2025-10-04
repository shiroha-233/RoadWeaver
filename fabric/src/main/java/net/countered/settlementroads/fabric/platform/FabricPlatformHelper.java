package net.countered.settlementroads.fabric.platform;

import net.countered.settlementroads.fabric.events.FabricEventHandler;
import net.countered.settlementroads.fabric.persistence.FabricWorldDataAttachment;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * Fabric平台实现
 */
public class FabricPlatformHelper {
    
    public static String getPlatformName() {
        return "Fabric";
    }
    
    public static boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
    
    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir();
    }
    
    public static void registerWorldDataAttachments() {
        FabricWorldDataAttachment.register();
    }
    
    public static void registerEventHandlers() {
        FabricEventHandler.register();
    }
    
    public static void registerClientEventHandlers() {
        FabricEventHandler.registerClient();
    }
    
    public static boolean isPhysicalClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }
    
    public static MinecraftServer getServerInClient() {
        return MinecraftClient.getInstance().getServer();
    }
}

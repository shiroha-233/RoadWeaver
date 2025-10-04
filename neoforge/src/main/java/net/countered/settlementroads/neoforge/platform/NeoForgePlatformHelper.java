package net.countered.settlementroads.neoforge.platform;

import net.countered.settlementroads.neoforge.events.NeoForgeEventHandler;
import net.countered.settlementroads.neoforge.persistence.NeoForgeWorldDataStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * NeoForge平台实现
 */
public class NeoForgePlatformHelper {
    
    public static String getPlatformName() {
        return "NeoForge";
    }
    
    public static boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
    
    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get();
    }
    
    public static void registerWorldDataAttachments() {
        NeoForgeWorldDataStorage.register();
    }
    
    public static void registerEventHandlers() {
        NeoForgeEventHandler.register();
    }
    
    public static void registerClientEventHandlers() {
        NeoForgeEventHandler.registerClient();
    }
    
    public static boolean isPhysicalClient() {
        return FMLEnvironment.dist.isClient();
    }
    
    public static MinecraftServer getServerInClient() {
        return Minecraft.getInstance().getSingleplayerServer();
    }
}

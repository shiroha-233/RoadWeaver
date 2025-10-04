package net.countered.settlementroads.client;

import net.countered.settlementroads.client.gui.RoadDebugScreen;
import net.countered.settlementroads.config.ConfigScreenFactory;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper.RoadDataStorage;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper.StructureConnectionsData;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper.StructureLocationsData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static net.countered.settlementroads.SettlementRoads.MOD_ID;

@Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public class SettlementRoadsClient {

    private static final KeyMapping DEBUG_MAP_KEY = new KeyMapping(
            "key.roadweaver.debug_map",
            GLFW.GLFW_KEY_H,
            "category.roadweaver"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DEBUG_MAP_KEY);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 注册配置界面工厂
        ConfigScreenFactory.register();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        while (DEBUG_MAP_KEY.consumeClick()) {
            handleDebugMapKey(client);
        }
    }

    private static void handleDebugMapKey(Minecraft client) {
        if (client.screen instanceof RoadDebugScreen) {
            client.setScreen(null);
            return;
        }

        if (!client.hasSingleplayerServer()) {
            return;
        }

        ServerLevel serverLevel = client.getSingleplayerServer().overworld();
        if (serverLevel == null) {
            return;
        }

        StructureLocationsData locationsData = WorldDataHelper.structureLocations(serverLevel);
        StructureConnectionsData connectionsData = WorldDataHelper.structureConnections(serverLevel);
        RoadDataStorage roadDataStorage = WorldDataHelper.roadData(serverLevel);

        List<BlockPos> structures = new ArrayList<>(locationsData.getLocations());
        List<Records.StructureConnection> connections = new ArrayList<>(connectionsData.getConnections());
        List<Records.RoadData> roads = new ArrayList<>(roadDataStorage.getRoadData());

        client.setScreen(new RoadDebugScreen(structures, connections, roads));
    }
}

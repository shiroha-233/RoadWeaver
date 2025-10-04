package net.countered.settlementroads.helpers;

import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.platform.services.WorldDataStorage;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class StructureConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoadWeaver.MOD_ID);
    public static Queue<Records.StructureConnection> cachedStructureConnections = new ArrayDeque<>();
    
    public static void cacheNewConnection(ServerWorld serverWorld, boolean locateAtPlayer) {
        StructureLocator.locateConfiguredStructure(serverWorld, 1, locateAtPlayer);
        Records.StructureLocationData locationData = WorldDataStorage.getStructureLocations(serverWorld);
        List<BlockPos> villagePosList = locationData.structureLocations();
        if (villagePosList == null || villagePosList.size() < 2) {
            return;
        }
        createNewStructureConnection(serverWorld);
    }

    private static void createNewStructureConnection(ServerWorld serverWorld) {
        Records.StructureLocationData structureLocationData = WorldDataStorage.getStructureLocations(serverWorld);
        List<BlockPos> villagePosList = structureLocationData.structureLocations();
        BlockPos latestVillagePos = villagePosList.get(villagePosList.size() - 1);
        List<BlockPos> worldStructureLocations = structureLocationData.structureLocations();

        BlockPos closestVillage = findClosestStructure(latestVillagePos, worldStructureLocations);

        if (closestVillage != null) {
            List<Records.StructureConnection> connections = new ArrayList<>(
                    WorldDataStorage.getStructureConnections(serverWorld)
            );
            if (!connectionExists(connections, latestVillagePos, closestVillage)) {
                Records.StructureConnection structureConnection = new Records.StructureConnection(latestVillagePos, closestVillage);
                connections.add(structureConnection);
                WorldDataStorage.setStructureConnections(serverWorld, connections);
                cachedStructureConnections.add(structureConnection);
                LOGGER.debug("Created connection between {} and {} (distance: {} blocks)", 
                    latestVillagePos, closestVillage, 
                    Math.sqrt(latestVillagePos.getSquaredDistance(closestVillage)));
            }
        }
    }

    private static boolean connectionExists(List<Records.StructureConnection> existingConnections, BlockPos a, BlockPos b) {
        for (Records.StructureConnection connection : existingConnections) {
            if ((connection.from().equals(a) && connection.to().equals(b)) ||
                    (connection.to().equals(b) && connection.from().equals(a))) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos findClosestStructure(BlockPos currentVillage, List<BlockPos> allVillages) {
        BlockPos closestVillage = null;
        double minDistance = Double.MAX_VALUE;

        for (BlockPos village : allVillages) {
            if (!village.equals(currentVillage)) {
                double distance = currentVillage.getSquaredDistance(village);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestVillage = village;
                }
            }
        }
        return closestVillage;
    }
}

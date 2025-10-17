package net.shiroha233.roadweaver.config.fabric;

import net.shiroha233.roadweaver.config.IModConfig;

import java.util.List;

/**
 * Adapter class to bridge FabricModConfig with IModConfig interface
 */
public class FabricModConfigAdapter implements IModConfig {
    
    @Override
    public List<String> structuresToLocate() {
        return FabricModConfig.getStructuresToLocate();
    }

    @Override
    public int structureSearchRadius() {
        return FabricModConfig.getStructureSearchRadius();
    }

    @Override
    public int initialLocatingCount() {
        return FabricModConfig.getInitialLocatingCount();
    }

    @Override
    public int maxConcurrentRoadGeneration() {
        return FabricModConfig.getMaxConcurrentRoadGeneration();
    }

    @Override
    public int structureSearchTriggerDistance() {
        return FabricModConfig.getStructureSearchTriggerDistance();
    }

    @Override
    public int structureBatchSize() {
        return FabricModConfig.getStructureBatchSize();
    }

    @Override
    public int structureSearchThreads() {
        return FabricModConfig.getStructureSearchThreads();
    }
    
    @Override
    public boolean enableAsyncStructureSearch() {
        return FabricModConfig.getEnableAsyncStructureSearch();
    }

    @Override
    public int averagingRadius() {
        return FabricModConfig.getAveragingRadius();
    }

    @Override
    public boolean allowArtificial() {
        return FabricModConfig.getAllowArtificial();
    }

    @Override
    public boolean allowNatural() {
        return FabricModConfig.getAllowNatural();
    }

    @Override
    public boolean placeWaypoints() {
        return FabricModConfig.getPlaceWaypoints();
    }

    @Override
    public boolean placeRoadFences() {
        return FabricModConfig.getPlaceRoadFences();
    }

    @Override
    public boolean placeSwings() {
        return FabricModConfig.getPlaceSwings();
    }

    @Override
    public boolean placeBenches() {
        return FabricModConfig.getPlaceBenches();
    }

    @Override
    public boolean placeGloriettes() {
        return FabricModConfig.getPlaceGloriettes();
    }

    @Override
    public int structureDistanceFromRoad() {
        return FabricModConfig.getStructureDistanceFromRoad();
    }

    @Override
    public int maxHeightDifference() {
        return FabricModConfig.getMaxHeightDifference();
    }

    @Override
    public int maxTerrainStability() {
        return FabricModConfig.getMaxTerrainStability();
    }

    //效果
    @Override
    public int getPowerOfTheRoadSpeedLevel() {
        return FabricModConfig.getPowerOfTheRoadSpeedLevel();
    }
    @Override
    public int getPowerOfTheRoadRegenerationLevel() {
        return FabricModConfig.getPowerOfTheRoadRegenerationLevel();
    }
    @Override
    public int getPowerOfTheRoadJumpLevel() {
        return FabricModConfig.getPowerOfTheRoadJumpLevel();
    }

}

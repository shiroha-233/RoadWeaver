package net.shiroha233.roadweaver.config.forge;

import net.shiroha233.roadweaver.config.IModConfig;

import java.util.List;

public class ForgeModConfigAdapter implements IModConfig {
    
    @Override
    public List<String> structuresToLocate() {
        return ForgeJsonConfig.getStructuresToLocate();
    }

    @Override
    public int structureSearchRadius() {
        return ForgeJsonConfig.getStructureSearchRadius();
    }

    @Override
    public int initialLocatingCount() {
        return ForgeJsonConfig.getInitialLocatingCount();
    }

    @Override
    public int maxConcurrentRoadGeneration() {
        return ForgeJsonConfig.getMaxConcurrentRoadGeneration();
    }

    @Override
    public int structureSearchTriggerDistance() {
        return ForgeJsonConfig.getStructureSearchTriggerDistance();
    }

    @Override
    public int structureBatchSize() {
        return ForgeJsonConfig.getStructureBatchSize();
    }

    @Override
    public int structureSearchThreads() {
        return ForgeJsonConfig.getStructureSearchThreads();
    }
    
    @Override
    public boolean enableAsyncStructureSearch() {
        return ForgeJsonConfig.getEnableAsyncStructureSearch();
    }

    @Override
    public int averagingRadius() {
        return ForgeJsonConfig.getAveragingRadius();
    }

    @Override
    public boolean allowArtificial() {
        return ForgeJsonConfig.getAllowArtificial();
    }

    @Override
    public boolean allowNatural() {
        return ForgeJsonConfig.getAllowNatural();
    }

    @Override
    public boolean placeWaypoints() {
        return ForgeJsonConfig.getPlaceWaypoints();
    }

    @Override
    public boolean placeRoadFences() {
        return ForgeJsonConfig.getPlaceRoadFences();
    }

    @Override
    public boolean placeSwings() {
        return ForgeJsonConfig.getPlaceSwings();
    }

    @Override
    public boolean placeBenches() {
        return ForgeJsonConfig.getPlaceBenches();
    }

    @Override
    public boolean placeGloriettes() {
        return ForgeJsonConfig.getPlaceGloriettes();
    }

    @Override
    public int structureDistanceFromRoad() {
        return ForgeJsonConfig.getStructureDistanceFromRoad();
    }

    @Override
    public int maxHeightDifference() {
        return ForgeJsonConfig.getMaxHeightDifference();
    }

    @Override
    public int maxTerrainStability() {
        return ForgeJsonConfig.getMaxTerrainStability();
    }

    @Override
    public int getPowerOfTheRoadSpeedLevel() {
        return ForgeJsonConfig.getPowerOfTheRoadSpeedLevel();
    }
    @Override
    public int getPowerOfTheRoadRegenerationLevel() {
        return ForgeJsonConfig.getPowerOfTheRoadRegenerationLevel();
    }
    @Override
    public int getPowerOfTheRoadJumpLevel() {
        return ForgeJsonConfig.getPowerOfTheRoadJumpLevel();
    }
}

package net.shiroha233.roadweaver.config;

import java.util.List;

/**
 * 配置包装器
 * 在多人游戏客户端时使用服务端同步的配置
 * 在单人游戏或服务端时使用本地配置
 */
public class SyncedConfigWrapper implements IModConfig {
    private final IModConfig localConfig;
    
    public SyncedConfigWrapper(IModConfig localConfig) {
        this.localConfig = localConfig;
    }
    
    @Override
    public List<String> structuresToLocate() {
        return ServerConfigHolder.getSynced("structuresToLocate", localConfig.structuresToLocate());
    }
    
    @Override
    public int structureSearchRadius() {
        return ServerConfigHolder.getSynced("structureSearchRadius", localConfig.structureSearchRadius());
    }
    
    @Override
    public int initialLocatingCount() {
        return ServerConfigHolder.getSynced("initialLocatingCount", localConfig.initialLocatingCount());
    }
    
    @Override
    public int maxConcurrentRoadGeneration() {
        return ServerConfigHolder.getSynced("maxConcurrentRoadGeneration", localConfig.maxConcurrentRoadGeneration());
    }
    
    @Override
    public int structureSearchTriggerDistance() {
        return ServerConfigHolder.getSynced("structureSearchTriggerDistance", localConfig.structureSearchTriggerDistance());
    }
    
    @Override
    public int structureBatchSize() {
        return ServerConfigHolder.getSynced("structureBatchSize", localConfig.structureBatchSize());
    }
    
    @Override
    public int structureSearchThreads() {
        return ServerConfigHolder.getSynced("structureSearchThreads", localConfig.structureSearchThreads());
    }
    
    @Override
    public boolean enableAsyncStructureSearch() {
        return ServerConfigHolder.getSynced("enableAsyncStructureSearch", localConfig.enableAsyncStructureSearch());
    }
    
    @Override
    public int averagingRadius() {
        return ServerConfigHolder.getSynced("averagingRadius", localConfig.averagingRadius());
    }
    
    @Override
    public boolean allowArtificial() {
        return ServerConfigHolder.getSynced("allowArtificial", localConfig.allowArtificial());
    }
    
    @Override
    public boolean allowNatural() {
        return ServerConfigHolder.getSynced("allowNatural", localConfig.allowNatural());
    }
    
    @Override
    public boolean placeWaypoints() {
        return ServerConfigHolder.getSynced("placeWaypoints", localConfig.placeWaypoints());
    }
    
    @Override
    public boolean placeRoadFences() {
        return ServerConfigHolder.getSynced("placeRoadFences", localConfig.placeRoadFences());
    }
    
    @Override
    public boolean placeSwings() {
        return ServerConfigHolder.getSynced("placeSwings", localConfig.placeSwings());
    }
    
    @Override
    public boolean placeBenches() {
        return ServerConfigHolder.getSynced("placeBenches", localConfig.placeBenches());
    }
    
    @Override
    public boolean placeGloriettes() {
        return ServerConfigHolder.getSynced("placeGloriettes", localConfig.placeGloriettes());
    }
    
    @Override
    public int structureDistanceFromRoad() {
        return ServerConfigHolder.getSynced("structureDistanceFromRoad", localConfig.structureDistanceFromRoad());
    }
    
    @Override
    public int maxHeightDifference() {
        return ServerConfigHolder.getSynced("maxHeightDifference", localConfig.maxHeightDifference());
    }
    
    @Override
    public int maxTerrainStability() {
        return ServerConfigHolder.getSynced("maxTerrainStability", localConfig.maxTerrainStability());
    }

    //效果
    @Override
    public int getPowerOfTheRoadSpeedLevel() {
        return ServerConfigHolder.getSynced("powerOfTheRoadSpeedLevel", localConfig.getPowerOfTheRoadSpeedLevel());
    }
    @Override
    public int getPowerOfTheRoadRegenerationLevel() {
        return ServerConfigHolder.getSynced("powerOfTheRoadRegenerationLevel", localConfig.getPowerOfTheRoadRegenerationLevel());
    }
    @Override
    public int getPowerOfTheRoadJumpLevel() {
        return ServerConfigHolder.getSynced("powerOfTheRoadJumpLevel", localConfig.getPowerOfTheRoadJumpLevel());
    }
}

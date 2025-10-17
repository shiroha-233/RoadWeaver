package net.shiroha233.roadweaver.config;

import java.util.List;

public interface IModConfig {
    // Structures
    List<String> structuresToLocate();
    int structureSearchRadius();

    // Pre-generation
    int initialLocatingCount();
    int maxConcurrentRoadGeneration();
    int structureSearchTriggerDistance();
    int structureBatchSize(); // 批量累积：累积多少个结构后再统一加入道路规划
    int structureSearchThreads(); // 结构搜索线程池大小
    boolean enableAsyncStructureSearch(); // 是否启用异步多线程结构搜索

    // Roads
    int averagingRadius();
    boolean allowArtificial();
    boolean allowNatural();
    boolean placeWaypoints();
    boolean placeRoadFences();
    boolean placeSwings();
    boolean placeBenches();
    boolean placeGloriettes();
    int structureDistanceFromRoad();
    int maxHeightDifference();
    int maxTerrainStability();

    int getPowerOfTheRoadSpeedLevel();
    int getPowerOfTheRoadRegenerationLevel();
    int getPowerOfTheRoadJumpLevel();
    // 性能配置
    default int heightCacheMaxSize() {
        return 100_000; // 默认10万个条目
    }
}

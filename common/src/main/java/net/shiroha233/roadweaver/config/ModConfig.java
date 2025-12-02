package net.shiroha233.roadweaver.config;

import java.util.ArrayList;
import java.util.List;

public final class ModConfig {
    public enum PlanningAlgorithm {
        KNN,
        DELAUNAY,
        RNG
    }

    public enum PathfindingAlgorithm {
        ASTAR_BASIC,
        ASTAR_BIDIRECTIONAL,
        GRADIENT_DESCENT
    }

    private boolean villagePredictionEnabled;
    private int predictRadiusChunks;
    private boolean biomePrefilter;
    private List<String> structureWhitelist;
    private List<String> structureBlacklist;

    // 路网规划配置
    private int initialPlanRadiusChunks; // 新建世界后以出生点为中心的初始规划半径（区块）
    private boolean dynamicPlanEnabled; // 是否启用基于玩家的动态增量规划
    private int dynamicPlanRadiusChunks; // 玩家为中心的动态规划半径（区块）
    private int dynamicPlanStrideChunks; // 动态规划触发步进（区块），用于判定玩家移动到新网格时触发
    private PlanningAlgorithm planningAlgorithm; // 路网连边算法

    // 道路生成配置
    private boolean allowArtificial;
    private boolean allowNatural;
    private boolean placeWaypoints;
    private int averagingRadius;
    private int generationThreads;
    private int computeThreads; // 计算线程池大小（0=自动，>0=固定值）
    private int initialGenerationThreads; // 初始生成专用线程数
    private int maxConcurrentGenerations;
    private int threadDutyCycle; // 线程占空比（1-100%），控制CPU使用率
    private int aStarStep; // A* 采样步长（方块）
    private int aStarMaxSteps; // A* 寻路最大步数上限
    private int causewayMaxDepth;
    private int maxSlopeStepPerTwoSegments;
    private boolean slopeLimitEnabled = true; // 是否启用基于 maxSlopeStepPerTwoSegments 的限坡平滑
    private PathfindingAlgorithm pathfindingAlgorithm; // 具体寻路算法策略

    private int roadWidth;
    private int lampInterval;
    private int roadClearHeight;
    private boolean tunnelEnabled;
    private int tunnelClearHeight;
    private boolean preventTreesOnRoad; // 阻止树木在道路上生成

    // 桥梁配置
    private boolean bridgeEnabled;
    private int bridgeDeckClearance;
    private boolean bridgeRailingEnabled;
    private int bridgePierInterval;
    private int bridgePierWidth;
    private int bridgePierMaxHeight;
    private boolean bridgeKeepLamps;
    private int bridgeRampSegments;
    private int bridgeMinWaterDepth;   // 最小水深，低于此值不建桥
    private int bridgeMinLength;       // 最小桥梁长度（段数），太短的桥跳过
    private int bridgeMergeGap;        // 桥梁区间合并间隔，间隔小于此值的区间合并

    // 路边结构配置
    private boolean roadsideStructuresEnabled;
    private int maxStructuresPerRoad;      // 每条道路最多放置的结构数
    private int minStructureSpacing;       // 两个结构之间的最小间隔（方块）
    private int smallStructureOffset;      // 小型结构距道路中心的距离
    private int mediumStructureOffset;     // 中型结构距道路中心的距离
    private int largeStructureOffset;      // 大型结构距道路中心的距离

    // 结构距离控制
    private int structureRoadOffset; // 道路端点距结构中心的缩进距离（方块）

    // A* 寻路成本权重
    private double orthoStepCost;
    private double diagStepCost;
    private int elevationWeight;
    private int biomeWeight;
    private int stabilityWeight;
    private int waterDepthWeight;
    private int nearWaterCost;
    private int waterProximityCost;
    private double heuristicWeight;
    private double deviationWeight;

    public ModConfig() {
        this.villagePredictionEnabled = true;
        this.predictRadiusChunks = 1024;
        this.biomePrefilter = true;
        this.structureWhitelist = new ArrayList<>();
        this.structureBlacklist = new ArrayList<>();
        this.structureWhitelist.add("#minecraft:village");

        // 默认规划参数：初始64区块；动态规划开启，半径256区块
        this.initialPlanRadiusChunks = 64;
        this.dynamicPlanEnabled = true;
        this.dynamicPlanRadiusChunks = 256;
        this.dynamicPlanStrideChunks = Math.max(8, Math.min(64, this.dynamicPlanRadiusChunks / 2));
        this.planningAlgorithm = PlanningAlgorithm.RNG;

        // 道路生成默认参数
        this.allowArtificial = true;
        this.allowNatural = true;
        this.placeWaypoints = false;
        this.averagingRadius = 8;

        this.generationThreads = Math.max(2, Math.min(3, Runtime.getRuntime().availableProcessors()));
        // computeThreads=0 表示自动模式：在 ThreadPoolManager 中按 CPU-1 计算
        this.computeThreads = 0;
        this.initialGenerationThreads = 6; // 初始生成默认6个线程
        this.maxConcurrentGenerations = Math.max(1, Math.min(3, this.generationThreads));
        this.threadDutyCycle = 50; // 默认50%占空比，降低CPU占用
        this.aStarStep = 16;
        this.aStarMaxSteps = 10000;
        this.causewayMaxDepth = 1;
        this.maxSlopeStepPerTwoSegments = 1;
        this.slopeLimitEnabled = true;
        this.pathfindingAlgorithm = PathfindingAlgorithm.ASTAR_BASIC;

        // 新增默认值
        this.roadWidth = 3;
        this.lampInterval = 32;
        this.roadClearHeight = 4;
        this.tunnelEnabled = false;
        this.tunnelClearHeight = 5;
        this.preventTreesOnRoad = true; // 默认开启

        // 桥梁默认值
        this.bridgeEnabled = true;
        this.bridgeDeckClearance = 2;
        this.bridgeRailingEnabled = true;
        this.bridgePierInterval = 6;
        this.bridgePierWidth = 1;
        this.bridgePierMaxHeight = 20;
        this.bridgeKeepLamps = true;
        this.bridgeRampSegments = 4;
        this.bridgeMinWaterDepth = 2;   // 水深至少2格才建桥
        this.bridgeMinLength = 5;       // 桥至少5段才建，避免小水坑
        this.bridgeMergeGap = 8;        // 间隔小于8段的桥梁区间合并

        // 路边结构默认值
        this.roadsideStructuresEnabled = true;
        this.maxStructuresPerRoad = 3;        // 每条道路最多3个结构
        this.minStructureSpacing = 64;        // 结构间最小间隔64格
        this.smallStructureOffset = 8;        // 小型结构距道路8格
        this.mediumStructureOffset = 12;      // 中型结构距道路12格
        this.largeStructureOffset = 16;       // 大型结构距道路16格

        // 结构距离控制默认值
        this.structureRoadOffset = 60; // 道路端点默认缩进 60 格

        // A* 寻路成本权重
        this.orthoStepCost = 1.0;
        this.diagStepCost = 1.0;
        this.elevationWeight = 80;
        this.biomeWeight = 2;
        this.stabilityWeight = 15;
        this.waterDepthWeight = 80;
        this.nearWaterCost = 80;
        this.waterProximityCost = 20;
        this.heuristicWeight = 15.0;
        this.deviationWeight = 0.5;
    }

    public boolean villagePredictionEnabled() {
        return villagePredictionEnabled;
    }

    public void setVillagePredictionEnabled(boolean villagePredictionEnabled) {
        this.villagePredictionEnabled = villagePredictionEnabled;
    }

    public int predictRadiusChunks() {
        return predictRadiusChunks;
    }

    public void setPredictRadiusChunks(int predictRadiusChunks) {
        this.predictRadiusChunks = predictRadiusChunks;
    }

    public boolean biomePrefilter() {
        return biomePrefilter;
    }

    public void setBiomePrefilter(boolean biomePrefilter) {
        this.biomePrefilter = biomePrefilter;
    }

    public List<String> structureWhitelist() {
        return structureWhitelist;
    }

    public void setStructureWhitelist(List<String> structureWhitelist) {
        this.structureWhitelist = structureWhitelist == null ? new ArrayList<>() : new ArrayList<>(structureWhitelist);
    }

    public List<String> structureBlacklist() {
        return structureBlacklist;
    }

    public void setStructureBlacklist(List<String> structureBlacklist) {
        this.structureBlacklist = structureBlacklist == null ? new ArrayList<>() : new ArrayList<>(structureBlacklist);
    }

    // 载入后修复缺省值与兼容项
    public void sanitize() {
        if (structureWhitelist == null)
            structureWhitelist = new ArrayList<>();
        if (structureBlacklist == null)
            structureBlacklist = new ArrayList<>();

        if (predictRadiusChunks <= 0)
            predictRadiusChunks = 1024;
        if (initialPlanRadiusChunks <= 0)
            initialPlanRadiusChunks = 64;
        if (dynamicPlanRadiusChunks <= 0)
            dynamicPlanRadiusChunks = 256;
        if (dynamicPlanStrideChunks <= 0)
            dynamicPlanStrideChunks = Math.max(8, Math.min(64, Math.max(1, dynamicPlanRadiusChunks) / 2));
        if (dynamicPlanStrideChunks > dynamicPlanRadiusChunks)
            dynamicPlanStrideChunks = dynamicPlanRadiusChunks;
        if (dynamicPlanStrideChunks > 256)
            dynamicPlanStrideChunks = 256;
        if (planningAlgorithm == null)
            planningAlgorithm = PlanningAlgorithm.RNG;
        if (aStarStep > 128)
            aStarStep = 128; // 步数上限
        if (aStarMaxSteps < 3000)
            aStarMaxSteps = 3000; // 最小步数下限
        if (aStarMaxSteps > 100000)
            aStarMaxSteps = 100000; // 最大步数上限
        if (causewayMaxDepth < 0)
            causewayMaxDepth = 0;// 最小填充深度
        if (causewayMaxDepth > 12)
            causewayMaxDepth = 12;// 最大填充深度
        if (maxSlopeStepPerTwoSegments < 0)
            maxSlopeStepPerTwoSegments = 0;// 最小斜坡步数
        if (maxSlopeStepPerTwoSegments > 8)
            maxSlopeStepPerTwoSegments = 8;// 最大斜坡步数

        // computeThreads 校验：0=自动模式，>0 时限制上限，防止配置过大
        if (computeThreads < 0)
            computeThreads = 0;
        if (computeThreads > 128)
            computeThreads = 128;

        // 线程占空比校验：1-100，0 或异常值回退到 50%（推荐）
        if (threadDutyCycle < 1 || threadDutyCycle > 100)
            threadDutyCycle = 50;

        if (pathfindingAlgorithm == null) {
            // 迁移旧配置
            pathfindingAlgorithm = PathfindingAlgorithm.ASTAR_BASIC;
        }

        // 新增字段校验
        if (roadWidth < 0)
            roadWidth = 0; // 0=自动
        if (roadWidth > 15)
            roadWidth = 15; // 宽度上限合理限制
        if (lampInterval < 1)
            lampInterval = 59; // 保底
        if (lampInterval > 2048)
            lampInterval = 2048;
        if (roadClearHeight < 1)
            roadClearHeight = 4;
        if (roadClearHeight > 16)
            roadClearHeight = 16;
        if (tunnelClearHeight < 2)
            tunnelClearHeight = 2;
        if (tunnelClearHeight > 16)
            tunnelClearHeight = 16;

        // 桥梁字段校验
        if (bridgeDeckClearance < 1)
            bridgeDeckClearance = 1;
        if (bridgeDeckClearance > 8)
            bridgeDeckClearance = 8;
        if (bridgePierInterval < 3)
            bridgePierInterval = 3;
        if (bridgePierInterval > 32)
            bridgePierInterval = 32;
        if (bridgePierWidth < 1)
            bridgePierWidth = 1;
        if (bridgePierWidth > 3)
            bridgePierWidth = 3;
        if (bridgePierMaxHeight < 6)
            bridgePierMaxHeight = 6;
        if (bridgePierMaxHeight > 64)
            bridgePierMaxHeight = 64;
        if (bridgeRampSegments < 0)
            bridgeRampSegments = 0;
        if (bridgeRampSegments > 12)
            bridgeRampSegments = 12;

        // A* 寻路成本权重校验
        if (orthoStepCost < 0)
            orthoStepCost = 0;
        if (diagStepCost < 0)
            diagStepCost = 0;
        if (elevationWeight < 0)
            elevationWeight = 0;
        if (biomeWeight < 0)
            biomeWeight = 0;
        if (stabilityWeight < 0)
            stabilityWeight = 0;
        if (waterDepthWeight < 0)
            waterDepthWeight = 0;
        if (nearWaterCost < 0)
            nearWaterCost = 0;
        if (waterProximityCost < 0)
            waterProximityCost = 0;
        if (heuristicWeight < 0)
            heuristicWeight = 0;
        if (deviationWeight < 0)
            deviationWeight = 0;

        // 路边结构配置校验
        if (maxStructuresPerRoad < 0)
            maxStructuresPerRoad = 0;
        if (maxStructuresPerRoad > 20)
            maxStructuresPerRoad = 20;
        if (minStructureSpacing < 1)
            minStructureSpacing = 1;
        if (minStructureSpacing > 256)
            minStructureSpacing = 256;
        if (smallStructureOffset < 1)
            smallStructureOffset = 1;
        if (smallStructureOffset > 64)
            smallStructureOffset = 64;
        if (mediumStructureOffset < 1)
            mediumStructureOffset = 1;
        if (mediumStructureOffset > 64)
            mediumStructureOffset = 64;
        if (largeStructureOffset < 1)
            largeStructureOffset = 1;
        if (largeStructureOffset > 64)
            largeStructureOffset = 64;

        // 结构距离控制校验
        if (structureRoadOffset < 0)
            structureRoadOffset = 0;
        if (structureRoadOffset > 256)
            structureRoadOffset = 256;
    }

    // 初始规划半径
    public int initialPlanRadiusChunks() { return initialPlanRadiusChunks; }
    public void setInitialPlanRadiusChunks(int v) { this.initialPlanRadiusChunks = v; }

    // 动态规划开关
    public boolean dynamicPlanEnabled() { return dynamicPlanEnabled; }
    public void setDynamicPlanEnabled(boolean v) { this.dynamicPlanEnabled = v; }

    // 动态规划半径
    public int dynamicPlanRadiusChunks() { return dynamicPlanRadiusChunks; }
    public void setDynamicPlanRadiusChunks(int v) { this.dynamicPlanRadiusChunks = v; }

    // 动态规划触发步进
    public int dynamicPlanStrideChunks() { return dynamicPlanStrideChunks; }
    public void setDynamicPlanStrideChunks(int v) { this.dynamicPlanStrideChunks = v; }

    public boolean allowArtificial() { return allowArtificial; }
    public void setAllowArtificial(boolean v) { this.allowArtificial = v; }

    public boolean allowNatural() { return allowNatural; }
    public void setAllowNatural(boolean v) { this.allowNatural = v; }

    public boolean placeWaypoints() { return placeWaypoints; }
    public void setPlaceWaypoints(boolean v) { this.placeWaypoints = v; }

    public int averagingRadius() { return averagingRadius; }
    public void setAveragingRadius(int v) { this.averagingRadius = v; }

    public int generationThreads() { return generationThreads; }
    public void setGenerationThreads(int v) { this.generationThreads = v; }

    // 计算线程池线程数（0=自动，>0=固定值）
    public int computeThreads() { return computeThreads; }
    public void setComputeThreads(int v) { this.computeThreads = v; }

    public int initialGenerationThreads() { return initialGenerationThreads; }
    public void setInitialGenerationThreads(int v) { this.initialGenerationThreads = v; }

    public int maxConcurrentGenerations() { return maxConcurrentGenerations; }
    public void setMaxConcurrentGenerations(int v) { this.maxConcurrentGenerations = v; }

    // 线程占空比（1-100%），用于控制CPU使用率
    public int threadDutyCycle() { return threadDutyCycle; }
    public void setThreadDutyCycle(int v) { this.threadDutyCycle = Math.max(1, Math.min(100, v)); }

    // A* 采样步长
    public int aStarStep() { return aStarStep; }
    public void setAStarStep(int v) { this.aStarStep = v; }

    // A* 最大步数
    public int aStarMaxSteps() { return aStarMaxSteps; }
    public void setAStarMaxSteps(int v) { this.aStarMaxSteps = v; }

    public int causewayMaxDepth() { return causewayMaxDepth; }
    public void setCausewayMaxDepth(int v) { this.causewayMaxDepth = v; }

    public int maxSlopeStepPerTwoSegments() { return maxSlopeStepPerTwoSegments; }
    public void setMaxSlopeStepPerTwoSegments(int v) { this.maxSlopeStepPerTwoSegments = v; }

    public boolean slopeLimitEnabled() { return slopeLimitEnabled; }
    public void setSlopeLimitEnabled(boolean v) { this.slopeLimitEnabled = v; }

    public PathfindingAlgorithm pathfindingAlgorithm() { return pathfindingAlgorithm; }
    public void setPathfindingAlgorithm(PathfindingAlgorithm v) { this.pathfindingAlgorithm = v; }

    // 新增：道路宽度（0=自动）
    public int roadWidth() { return roadWidth; }
    public void setRoadWidth(int v) { this.roadWidth = v; }

    // 新增：路灯间隔（段）
    public int lampInterval() { return lampInterval; }
    public void setLampInterval(int v) { this.lampInterval = v; }

    public int roadClearHeight() { return roadClearHeight; }
    public void setRoadClearHeight(int v) { this.roadClearHeight = v; }

    // 路网连边算法
    public PlanningAlgorithm planningAlgorithm() { return planningAlgorithm; }
    public void setPlanningAlgorithm(PlanningAlgorithm v) { this.planningAlgorithm = v; }

    public boolean tunnelEnabled() { return tunnelEnabled; }
    public void setTunnelEnabled(boolean v) { this.tunnelEnabled = v; }

    public int tunnelClearHeight() { return tunnelClearHeight; }
    public void setTunnelClearHeight(int v) { this.tunnelClearHeight = v; }

    public boolean preventTreesOnRoad() { return preventTreesOnRoad; }
    public void setPreventTreesOnRoad(boolean v) { this.preventTreesOnRoad = v; }

    // 桥梁配置存取
    public boolean bridgeEnabled() { return bridgeEnabled; }
    public void setBridgeEnabled(boolean v) { this.bridgeEnabled = v; }

    public int bridgeDeckClearance() { return bridgeDeckClearance; }
    public void setBridgeDeckClearance(int v) { this.bridgeDeckClearance = v; }

    public boolean bridgeRailingEnabled() { return bridgeRailingEnabled; }
    public void setBridgeRailingEnabled(boolean v) { this.bridgeRailingEnabled = v; }

    public int bridgePierInterval() { return bridgePierInterval; }
    public void setBridgePierInterval(int v) { this.bridgePierInterval = v; }

    public int bridgePierWidth() { return bridgePierWidth; }
    public void setBridgePierWidth(int v) { this.bridgePierWidth = v; }

    public int bridgePierMaxHeight() { return bridgePierMaxHeight; }
    public void setBridgePierMaxHeight(int v) { this.bridgePierMaxHeight = v; }

    public boolean bridgeKeepLamps() { return bridgeKeepLamps; }
    public void setBridgeKeepLamps(boolean v) { this.bridgeKeepLamps = v; }

    public int bridgeRampSegments() { return bridgeRampSegments; }
    public void setBridgeRampSegments(int v) { this.bridgeRampSegments = v; }

    public int bridgeMinWaterDepth() { return bridgeMinWaterDepth; }
    public void setBridgeMinWaterDepth(int v) { this.bridgeMinWaterDepth = v; }

    public int bridgeMinLength() { return bridgeMinLength; }
    public void setBridgeMinLength(int v) { this.bridgeMinLength = v; }

    public int bridgeMergeGap() { return bridgeMergeGap; }
    public void setBridgeMergeGap(int v) { this.bridgeMergeGap = v; }

    // A* 寻路成本权重
    public double orthoStepCost() { return orthoStepCost; }
    public void setOrthoStepCost(double v) { this.orthoStepCost = v; }

    public double diagStepCost() { return diagStepCost; }
    public void setDiagStepCost(double v) { this.diagStepCost = v; }

    public int elevationWeight() { return elevationWeight; }
    public void setElevationWeight(int v) { this.elevationWeight = v; }

    public int biomeWeight() { return biomeWeight; }
    public void setBiomeWeight(int v) { this.biomeWeight = v; }

    public int stabilityWeight() { return stabilityWeight; }
    public void setStabilityWeight(int v) { this.stabilityWeight = v; }

    public int waterDepthWeight() { return waterDepthWeight; }
    public void setWaterDepthWeight(int v) { this.waterDepthWeight = v; }

    public int nearWaterCost() { return nearWaterCost; }
    public void setNearWaterCost(int v) { this.nearWaterCost = v; }

    public int waterProximityCost() { return waterProximityCost; }
    public void setWaterProximityCost(int v) { this.waterProximityCost = v; }

    public double heuristicWeight() { return heuristicWeight; }
    public void setHeuristicWeight(double v) { this.heuristicWeight = v; }

    public double deviationWeight() { return deviationWeight; }
    public void setDeviationWeight(double v) { this.deviationWeight = v; }

    // 路边结构配置存取
    public boolean roadsideStructuresEnabled() { return roadsideStructuresEnabled; }
    public void setRoadsideStructuresEnabled(boolean v) { this.roadsideStructuresEnabled = v; }

    public int maxStructuresPerRoad() { return maxStructuresPerRoad; }
    public void setMaxStructuresPerRoad(int v) { this.maxStructuresPerRoad = Math.max(0, v); }

    public int minStructureSpacing() { return minStructureSpacing; }
    public void setMinStructureSpacing(int v) { this.minStructureSpacing = Math.max(1, v); }

    public int smallStructureOffset() { return smallStructureOffset; }
    public void setSmallStructureOffset(int v) { this.smallStructureOffset = Math.max(1, v); }

    public int mediumStructureOffset() { return mediumStructureOffset; }
    public void setMediumStructureOffset(int v) { this.mediumStructureOffset = Math.max(1, v); }

    public int largeStructureOffset() { return largeStructureOffset; }
    public void setLargeStructureOffset(int v) { this.largeStructureOffset = Math.max(1, v); }

    // 结构距离控制存取
    public int structureRoadOffset() { return structureRoadOffset; }
    public void setStructureRoadOffset(int v) { this.structureRoadOffset = v; }
}

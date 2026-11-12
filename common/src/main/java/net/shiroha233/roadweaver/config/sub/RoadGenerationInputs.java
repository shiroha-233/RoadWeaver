package net.shiroha233.roadweaver.config.sub;

/**
 * 道路生成配置的组装来源，承载从总配置中抽取的原始子配置与标量参数
 *
 * @param appearance        外观配置
 * @param pathfinding       寻路代价配置
 * @param bridge            桥梁配置
 * @param averagingRadius   高度平滑半径
 * @param slopeLimitEnabled 是否启用坡度限制
 */
public record RoadGenerationInputs(
        RoadAppearanceConfig appearance,
        PathfindingCostConfig pathfinding,
        BridgeConfig bridge,
        int averagingRadius,
        boolean slopeLimitEnabled) {
}

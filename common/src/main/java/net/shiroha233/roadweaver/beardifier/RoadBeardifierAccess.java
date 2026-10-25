package net.shiroha233.roadweaver.beardifier;

import net.shiroha233.roadweaver.worldgen.road.RoadChunkPlan;

/**
 * Beardifier 道路计划访问接口，通过平台 Mixin 注入。
 */
public interface RoadBeardifierAccess {
    void roadweaver$setRoadChunkPlan(RoadChunkPlan plan);

    RoadChunkPlan roadweaver$getRoadChunkPlan();
}

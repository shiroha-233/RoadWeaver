package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 路边结构配置
 */
public final class RoadsideStructureConfig implements SubConfig {
    private boolean enabled = true;
    private int maxStructuresPerRoad = RoadConstants.DEFAULT_MAX_STRUCTURES_PER_ROAD;
    private int smallStructureOffset = RoadConstants.DEFAULT_SMALL_STRUCTURE_OFFSET;
    private int mediumStructureOffset = RoadConstants.DEFAULT_MEDIUM_STRUCTURE_OFFSET;
    private int largeStructureOffset = RoadConstants.DEFAULT_LARGE_STRUCTURE_OFFSET;

    @Override
    public void sanitize() {
        maxStructuresPerRoad = Math.max(0, Math.min(RoadConstants.MAX_STRUCTURES_PER_ROAD_MAX, maxStructuresPerRoad));
        smallStructureOffset = Math.max(RoadConstants.STRUCTURE_OFFSET_MIN, Math.min(RoadConstants.STRUCTURE_OFFSET_MAX, smallStructureOffset));
        mediumStructureOffset = Math.max(RoadConstants.STRUCTURE_OFFSET_MIN, Math.min(RoadConstants.STRUCTURE_OFFSET_MAX, mediumStructureOffset));
        largeStructureOffset = Math.max(RoadConstants.STRUCTURE_OFFSET_MIN, Math.min(RoadConstants.STRUCTURE_OFFSET_MAX, largeStructureOffset));
    }

    @Override
    public RoadsideStructureConfig snapshot() {
        RoadsideStructureConfig copy = new RoadsideStructureConfig();
        copy.enabled = this.enabled;
        copy.maxStructuresPerRoad = this.maxStructuresPerRoad;
        copy.smallStructureOffset = this.smallStructureOffset;
        copy.mediumStructureOffset = this.mediumStructureOffset;
        copy.largeStructureOffset = this.largeStructureOffset;
        return copy;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int maxStructuresPerRoad() { return maxStructuresPerRoad; }
    public void setMaxStructuresPerRoad(int v) { this.maxStructuresPerRoad = Math.max(0, v); }
    public int smallStructureOffset() { return smallStructureOffset; }
    public void setSmallStructureOffset(int v) { this.smallStructureOffset = Math.max(1, v); }
    public int mediumStructureOffset() { return mediumStructureOffset; }
    public void setMediumStructureOffset(int v) { this.mediumStructureOffset = Math.max(1, v); }
    public int largeStructureOffset() { return largeStructureOffset; }
    public void setLargeStructureOffset(int v) { this.largeStructureOffset = Math.max(1, v); }
}

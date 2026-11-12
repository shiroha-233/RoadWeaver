package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 桥梁配置
 */
public final class BridgeConfig implements SubConfig {
    private boolean enabled = true;
    private int deckClearance = RoadConstants.DEFAULT_BRIDGE_DECK_CLEARANCE;
    private int maxLengthBlocks = RoadConstants.DEFAULT_BRIDGE_MAX_LENGTH_BLOCKS;
    private boolean useBuoysInstead = false;
    private boolean useBuoysWhenSkipped = false;
    private int buoyIntervalBlocks = RoadConstants.DEFAULT_BUOY_INTERVAL_BLOCKS;
    private int pierInterval = RoadConstants.DEFAULT_BRIDGE_PIER_INTERVAL;
    private int pierWidth = RoadConstants.DEFAULT_BRIDGE_PIER_WIDTH;
    private int pierMaxHeight = RoadConstants.DEFAULT_BRIDGE_PIER_MAX_HEIGHT;
    private boolean keepLamps = true;
    private int rampSegments = RoadConstants.DEFAULT_BRIDGE_RAMP_SEGMENTS;
    private int minWaterDepth = RoadConstants.DEFAULT_BRIDGE_MIN_WATER_DEPTH;
    private int minLength = RoadConstants.DEFAULT_BRIDGE_MIN_LENGTH;
    private int mergeGap = RoadConstants.DEFAULT_BRIDGE_MERGE_GAP;

    @Override
    public void sanitize() {
        deckClearance = Math.max(RoadConstants.BRIDGE_DECK_CLEARANCE_MIN, Math.min(RoadConstants.BRIDGE_DECK_CLEARANCE_MAX, deckClearance));
        maxLengthBlocks = Math.max(0, Math.min(RoadConstants.BRIDGE_MAX_LENGTH_MAX, maxLengthBlocks));
        buoyIntervalBlocks = Math.max(RoadConstants.BUOY_INTERVAL_MIN, Math.min(RoadConstants.BUOY_INTERVAL_MAX, buoyIntervalBlocks));
        pierInterval = Math.max(RoadConstants.BRIDGE_PIER_INTERVAL_MIN, Math.min(RoadConstants.BRIDGE_PIER_INTERVAL_MAX, pierInterval));
        pierWidth = Math.max(RoadConstants.BRIDGE_PIER_WIDTH_MIN, Math.min(RoadConstants.BRIDGE_PIER_WIDTH_MAX, pierWidth));
        pierMaxHeight = Math.max(RoadConstants.BRIDGE_PIER_MAX_HEIGHT_MIN, Math.min(RoadConstants.BRIDGE_PIER_MAX_HEIGHT_MAX, pierMaxHeight));
        rampSegments = Math.max(0, Math.min(RoadConstants.BRIDGE_RAMP_SEGMENTS_MAX, rampSegments));
    }

    @Override
    public BridgeConfig snapshot() {
        BridgeConfig copy = new BridgeConfig();
        copy.enabled = this.enabled;
        copy.deckClearance = this.deckClearance;
        copy.maxLengthBlocks = this.maxLengthBlocks;
        copy.useBuoysInstead = this.useBuoysInstead;
        copy.useBuoysWhenSkipped = this.useBuoysWhenSkipped;
        copy.buoyIntervalBlocks = this.buoyIntervalBlocks;
        copy.pierInterval = this.pierInterval;
        copy.pierWidth = this.pierWidth;
        copy.pierMaxHeight = this.pierMaxHeight;
        copy.keepLamps = this.keepLamps;
        copy.rampSegments = this.rampSegments;
        copy.minWaterDepth = this.minWaterDepth;
        copy.minLength = this.minLength;
        copy.mergeGap = this.mergeGap;
        return copy;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int deckClearance() { return deckClearance; }
    public void setDeckClearance(int v) { this.deckClearance = v; }
    public int maxLengthBlocks() { return maxLengthBlocks; }
    public void setMaxLengthBlocks(int v) { this.maxLengthBlocks = v; }
    public boolean useBuoysInstead() { return useBuoysInstead; }
    public void setUseBuoysInstead(boolean v) { this.useBuoysInstead = v; }
    public boolean useBuoysWhenSkipped() { return useBuoysWhenSkipped; }
    public void setUseBuoysWhenSkipped(boolean v) { this.useBuoysWhenSkipped = v; }
    public int buoyIntervalBlocks() { return buoyIntervalBlocks; }
    public void setBuoyIntervalBlocks(int v) { this.buoyIntervalBlocks = v; }
    public int pierInterval() { return pierInterval; }
    public void setPierInterval(int v) { this.pierInterval = v; }
    public int pierWidth() { return pierWidth; }
    public void setPierWidth(int v) { this.pierWidth = v; }
    public int pierMaxHeight() { return pierMaxHeight; }
    public void setPierMaxHeight(int v) { this.pierMaxHeight = v; }
    public boolean keepLamps() { return keepLamps; }
    public void setKeepLamps(boolean v) { this.keepLamps = v; }
    public int rampSegments() { return rampSegments; }
    public void setRampSegments(int v) { this.rampSegments = v; }
    public int minWaterDepth() { return minWaterDepth; }
    public void setMinWaterDepth(int v) { this.minWaterDepth = v; }
    public int minLength() { return minLength; }
    public void setMinLength(int v) { this.minLength = v; }
    public int mergeGap() { return mergeGap; }
    public void setMergeGap(int v) { this.mergeGap = v; }
}

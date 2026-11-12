package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 道路外观配置
 */
public final class RoadAppearanceConfig implements SubConfig {
    private Boolean roadsEnabled = true;
    private boolean allowArtificial = true;
    private boolean allowNatural = true;
    private boolean placeWaypoints = false;
    private boolean spawnCabinEnabled = true;
    private int averagingRadius = RoadConstants.DEFAULT_AVERAGING_RADIUS;
    private int roadWidth = RoadConstants.DEFAULT_ROAD_WIDTH;
    private boolean roadSignsEnabled = true;
    private int lampInterval = RoadConstants.DEFAULT_LAMP_INTERVAL;
    private int roadClearHeight = RoadConstants.DEFAULT_ROAD_CLEAR_HEIGHT;
    private boolean tunnelEnabled = false;
    private int tunnelClearHeight = RoadConstants.DEFAULT_TUNNEL_CLEAR_HEIGHT;
    private boolean preventTreesOnRoad = true;
    private int maxSlopeStepPerTwoSegments = RoadConstants.DEFAULT_MAX_SLOPE_STEP;
    private boolean slopeLimitEnabled = true;

    @Override
    public void sanitize() {
        if (roadsEnabled == null) roadsEnabled = true;
        roadWidth = Math.max(0, Math.min(RoadConstants.ROAD_WIDTH_MAX, roadWidth));
        lampInterval = Math.max(RoadConstants.LAMP_INTERVAL_MIN, Math.min(RoadConstants.LAMP_INTERVAL_MAX, lampInterval));
        roadClearHeight = Math.max(RoadConstants.ROAD_CLEAR_HEIGHT_MIN, Math.min(RoadConstants.ROAD_CLEAR_HEIGHT_MAX, roadClearHeight));
        tunnelClearHeight = Math.max(RoadConstants.TUNNEL_CLEAR_HEIGHT_MIN, Math.min(RoadConstants.TUNNEL_CLEAR_HEIGHT_MAX, tunnelClearHeight));
        maxSlopeStepPerTwoSegments = Math.max(0, Math.min(RoadConstants.MAX_SLOPE_STEP_MAX, maxSlopeStepPerTwoSegments));
    }

    @Override
    public RoadAppearanceConfig snapshot() {
        RoadAppearanceConfig copy = new RoadAppearanceConfig();
        copy.roadsEnabled = this.roadsEnabled;
        copy.allowArtificial = this.allowArtificial;
        copy.allowNatural = this.allowNatural;
        copy.placeWaypoints = this.placeWaypoints;
        copy.spawnCabinEnabled = this.spawnCabinEnabled;
        copy.averagingRadius = this.averagingRadius;
        copy.roadWidth = this.roadWidth;
        copy.roadSignsEnabled = this.roadSignsEnabled;
        copy.lampInterval = this.lampInterval;
        copy.roadClearHeight = this.roadClearHeight;
        copy.tunnelEnabled = this.tunnelEnabled;
        copy.tunnelClearHeight = this.tunnelClearHeight;
        copy.preventTreesOnRoad = this.preventTreesOnRoad;
        copy.maxSlopeStepPerTwoSegments = this.maxSlopeStepPerTwoSegments;
        copy.slopeLimitEnabled = this.slopeLimitEnabled;
        return copy;
    }

    public boolean roadsEnabled() { return roadsEnabled == null || roadsEnabled; }
    public void setRoadsEnabled(boolean v) { this.roadsEnabled = v; }
    public boolean allowArtificial() { return allowArtificial; }
    public void setAllowArtificial(boolean v) { this.allowArtificial = v; }
    public boolean allowNatural() { return allowNatural; }
    public void setAllowNatural(boolean v) { this.allowNatural = v; }
    public boolean placeWaypoints() { return placeWaypoints; }
    public void setPlaceWaypoints(boolean v) { this.placeWaypoints = v; }
    public boolean spawnCabinEnabled() { return spawnCabinEnabled; }
    public void setSpawnCabinEnabled(boolean v) { this.spawnCabinEnabled = v; }
    public int averagingRadius() { return averagingRadius; }
    public void setAveragingRadius(int v) { this.averagingRadius = v; }
    public int roadWidth() { return roadWidth; }
    public void setRoadWidth(int v) { this.roadWidth = v; }
    public boolean roadSignsEnabled() { return roadSignsEnabled; }
    public void setRoadSignsEnabled(boolean v) { this.roadSignsEnabled = v; }
    public int lampInterval() { return lampInterval; }
    public void setLampInterval(int v) { this.lampInterval = v; }
    public int roadClearHeight() { return roadClearHeight; }
    public void setRoadClearHeight(int v) { this.roadClearHeight = v; }
    public boolean tunnelEnabled() { return tunnelEnabled; }
    public void setTunnelEnabled(boolean v) { this.tunnelEnabled = v; }
    public int tunnelClearHeight() { return tunnelClearHeight; }
    public void setTunnelClearHeight(int v) { this.tunnelClearHeight = v; }
    public boolean preventTreesOnRoad() { return preventTreesOnRoad; }
    public void setPreventTreesOnRoad(boolean v) { this.preventTreesOnRoad = v; }
    public int maxSlopeStepPerTwoSegments() { return maxSlopeStepPerTwoSegments; }
    public void setMaxSlopeStepPerTwoSegments(int v) { this.maxSlopeStepPerTwoSegments = v; }
    public boolean slopeLimitEnabled() { return slopeLimitEnabled; }
    public void setSlopeLimitEnabled(boolean v) { this.slopeLimitEnabled = v; }
}

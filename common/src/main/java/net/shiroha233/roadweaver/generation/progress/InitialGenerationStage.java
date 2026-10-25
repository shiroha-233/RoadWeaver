package net.shiroha233.roadweaver.generation.progress;

/**
 * 初始世界生成阶段。
 */
public enum InitialGenerationStage {
    PLANNING(0, 15, "gui.roadweaver.initgen.stage.planning"),
    EXACT_SAMPLING(15, 30, "gui.roadweaver.initgen.stage.exact_sampling"),
    EXACT_PATHING(45, 15, "gui.roadweaver.initgen.stage.exact_pathing"),
    COARSE_SAMPLING(15, 30, "gui.roadweaver.initgen.stage.coarse_sampling"),
    COARSE_PATHING(45, 15, "gui.roadweaver.initgen.stage.coarse_pathing"),
    ROAD_GENERATION(60, 30, "gui.roadweaver.initgen.stage.road_generation"),
    POST_PROCESSING(90, 10, "gui.roadweaver.initgen.stage.post_processing"),
    FINISHED(100, 0, "gui.roadweaver.initgen.stage.finished");

    private final int basePercent;
    private final int weightPercent;
    private final String labelKey;

    InitialGenerationStage(int basePercent, int weightPercent, String labelKey) {
        this.basePercent = basePercent;
        this.weightPercent = weightPercent;
        this.labelKey = labelKey;
    }

    public int basePercent() {
        return basePercent;
    }

    public int weightPercent() {
        return weightPercent;
    }

    public String labelKey() {
        return labelKey;
    }
}

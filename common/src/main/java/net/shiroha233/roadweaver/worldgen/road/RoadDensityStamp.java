package net.shiroha233.roadweaver.worldgen.road;

import net.minecraft.world.level.ChunkPos;

import java.util.Arrays;

/**
 * 道路密度 stamp。几何投影和曲线计算在编译阶段完成，NOISE 热路径只做数组读取。
 */
public final class RoadDensityStamp {
    private static final int COLUMN_COUNT = 16 * 16;
    private static final int EMPTY_Y = Integer.MIN_VALUE;
    private static final int[] EMPTY_TARGETS = emptyTargets();
    private static final float[] EMPTY_LATERAL = new float[COLUMN_COUNT];
    private static final boolean[] EMPTY_OCCUPIED = new boolean[COLUMN_COUNT];
    private static final DepthProfile[] EMPTY_PROFILES = createEmptyProfiles();

    private final ChunkPos chunkPos;
    private final int[] fillTargetY;
    private final int[] carveTargetY;
    private final float[] fillLateral;
    private final float[] carveLateral;
    private final float[] fillDepth;
    private final float[] carveDepth;
    private final boolean[] occupied;
    private final boolean empty;

    RoadDensityStamp(ChunkPos chunkPos,
                     int[] fillTargetY,
                     int[] carveTargetY,
                     float[] fillLateral,
                     float[] carveLateral,
                     float[] fillDepth,
                     float[] carveDepth) {
        this(chunkPos, fillTargetY, carveTargetY, fillLateral, carveLateral,
                fillDepth, carveDepth, EMPTY_OCCUPIED, true);
    }

    RoadDensityStamp(ChunkPos chunkPos,
                             int[] fillTargetY,
                             int[] carveTargetY,
                             float[] fillLateral,
                             float[] carveLateral,
                             float[] fillDepth,
                             float[] carveDepth,
                             boolean[] occupied,
                             boolean copyArrays) {
        this.chunkPos = chunkPos;
        this.fillTargetY = copyArrays ? fillTargetY.clone() : fillTargetY;
        this.carveTargetY = copyArrays ? carveTargetY.clone() : carveTargetY;
        this.fillLateral = copyArrays ? fillLateral.clone() : fillLateral;
        this.carveLateral = copyArrays ? carveLateral.clone() : carveLateral;
        this.fillDepth = copyArrays ? fillDepth.clone() : fillDepth;
        this.carveDepth = copyArrays ? carveDepth.clone() : carveDepth;
        this.occupied = copyArrays ? occupied.clone() : occupied;
        validate(this.fillTargetY, this.carveTargetY, this.fillLateral, this.carveLateral, this.occupied);
        this.empty = allZero(this.fillLateral) && allZero(this.carveLateral);
    }

    public static RoadDensityStamp empty(ChunkPos chunkPos, int clearHeight) {
        return empty(chunkPos, clearHeight, EMPTY_OCCUPIED, false);
    }

    static RoadDensityStamp empty(ChunkPos chunkPos,
                                  int clearHeight,
                                  boolean[] occupied) {
        return empty(chunkPos, clearHeight, occupied, true);
    }

    private static RoadDensityStamp empty(ChunkPos chunkPos,
                                          int clearHeight,
                                          boolean[] occupied,
                                          boolean copyOccupied) {
        int safeClearHeight = Math.max(0, Math.min(16, clearHeight));
        DepthProfile profile = EMPTY_PROFILES[safeClearHeight];
        return new RoadDensityStamp(chunkPos, EMPTY_TARGETS, EMPTY_TARGETS,
                EMPTY_LATERAL, EMPTY_LATERAL, profile.fillDepth(), profile.carveDepth(),
                occupied, copyOccupied);
    }

    public ChunkPos chunkPos() {
        return chunkPos;
    }

    public boolean isEmpty() {
        return empty;
    }

    /**
     * 计算当前区块列的道路贡献。调用方保证 x/z 属于此 stamp 的区块。
     */
    public double density(int x, int y, int z) {
        int localX = x - chunkPos.getMinBlockX();
        int localZ = z - chunkPos.getMinBlockZ();
        if ((localX | localZ) < 0 || localX >= 16 || localZ >= 16) return 0.0D;

        int index = localX | (localZ << 4);
        int fillY = fillTargetY[index];
        if (fillY != EMPTY_Y && y < fillY) {
            int depth = fillY - 1 - y;
            if (depth < fillDepth.length) {
                double fill = fillLateral[index] * fillDepth[depth];
                if (fill != 0.0D) return fill;
            }
        }

        int carveY = carveTargetY[index];
        int above = carveY == EMPTY_Y ? 0 : y - carveY;
        if (above > 0 && above < carveDepth.length) {
            double carve = carveLateral[index] * carveDepth[above];
            return carve == 0.0D ? 0.0D : carve;
        }
        return 0.0D;
    }

    public int targetY(int localX, int localZ) {
        if ((localX | localZ) < 0 || localX >= 16 || localZ >= 16) return EMPTY_Y;
        int index = localX | (localZ << 4);
        return fillTargetY[index] != EMPTY_Y ? fillTargetY[index] : carveTargetY[index];
    }

    public float fillLateral(int localX, int localZ) {
        if ((localX | localZ) < 0 || localX >= 16 || localZ >= 16) return 0.0F;
        return fillLateral[localX | (localZ << 4)];
    }

    public float carveLateral(int localX, int localZ) {
        if ((localX | localZ) < 0 || localX >= 16 || localZ >= 16) return 0.0F;
        return carveLateral[localX | (localZ << 4)];
    }

    public boolean isOccupied(int localX, int localZ) {
        if ((localX | localZ) < 0 || localX >= 16 || localZ >= 16) return false;
        return occupied[localX | (localZ << 4)];
    }

    private static void validate(int[] fillTargetY,
                                 int[] carveTargetY,
                                 float[] fillLateral,
                                 float[] carveLateral,
                                 boolean[] occupied) {
        if (fillTargetY.length != COLUMN_COUNT
                || carveTargetY.length != COLUMN_COUNT
                || fillLateral.length != COLUMN_COUNT
                || carveLateral.length != COLUMN_COUNT
                || occupied.length != COLUMN_COUNT) {
            throw new IllegalArgumentException("road density stamp must contain 256 columns");
        }
    }

    private static boolean allZero(float[] values) {
        for (float value : values) {
            if (value != 0.0F) return false;
        }
        return true;
    }

    private static int[] emptyTargets() {
        int[] targets = new int[COLUMN_COUNT];
        Arrays.fill(targets, EMPTY_Y);
        return targets;
    }

    private static DepthProfile[] createEmptyProfiles() {
        DepthProfile[] profiles = new DepthProfile[17];
        for (int clearHeight = 0; clearHeight < profiles.length; clearHeight++) {
            float[] fillDepth = new float[7];
            float[] carveDepth = new float[clearHeight + 1];
            for (int i = 0; i < fillDepth.length; i++) {
                fillDepth[i] = 0.5F * (float) Math.pow(
                        1.0D - Math.min(1.0D, (i + 0.5D) / 7.0D), 2.2D);
            }
            for (int i = 0; i < carveDepth.length; i++) {
                carveDepth[i] = clearHeight == 0 ? 0.0F : -0.6F * (float) Math.pow(
                        1.0D - Math.min(1.0D, i / (double) clearHeight), 1.8D);
            }
            profiles[clearHeight] = new DepthProfile(fillDepth, carveDepth);
        }
        return profiles;
    }

    private record DepthProfile(float[] fillDepth, float[] carveDepth) {
    }
}

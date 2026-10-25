package net.shiroha233.roadweaver.planning.path;

import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprint;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 待生成路径的配置身份计算服务。
 */
public final class PlannedPathFingerprintService {
    private static final String DOMAIN = "roadweaver:planned_path:v1";

    private PlannedPathFingerprintService() {}

    public static String create(WorldgenFingerprint worldgen,
                                TerrainSamplingMode effectiveMode,
                                PathfindingCostConfig config) {
        Objects.requireNonNull(worldgen, "worldgen");
        Objects.requireNonNull(effectiveMode, "effectiveMode");
        Objects.requireNonNull(config, "config");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putString(digest, DOMAIN);
            putString(digest, worldgen.namespace());
            putInt(digest, worldgen.schemaVersion());
            putInt(digest, worldgen.dataVersion());
            putString(digest, effectiveMode.name());
            putString(digest, config.pathfindingAlgorithm().name());
            putDouble(digest, config.orthoStepCost());
            putDouble(digest, config.diagStepCost());
            putInt(digest, config.elevationWeight());
            putInt(digest, config.biomeWeight());
            putInt(digest, config.stabilityWeight());
            putInt(digest, config.waterDepthWeight());
            putInt(digest, config.nearWaterCost());
            putInt(digest, config.waterProximityCost());
            putDouble(digest, config.heuristicWeight());
            putDouble(digest, config.deviationWeight());
            putInt(digest, config.effectiveAStarStep());
            putInt(digest, config.aStarMaxSteps());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void putString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        putInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void putDouble(MessageDigest digest, double value) {
        digest.update(ByteBuffer.allocate(Double.BYTES).putDouble(value).array());
    }
}

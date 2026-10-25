package net.shiroha233.roadweaver.search;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.map.MapPatchService;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class StructureIndexService {

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final boolean CACHE_DEBUG = Boolean.getBoolean("roadweaver.structureCacheDebug");

    private StructureIndexService() {
    }

    public static List<StructureInfo> predictAndVerifyAroundSpawn(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            return List.of();
        }
        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.structurePredictionEnabled()) {
            return List.of();
        }
        BlockPos spawn = level.getSharedSpawnPos();
        int radiusChunks = Math.max(1, cfg.predictRadiusChunks());
        int minBlockX = (spawn.getX() >> 4) - radiusChunks;
        int maxBlockX = (spawn.getX() >> 4) + radiusChunks;
        int minBlockZ = (spawn.getZ() >> 4) - radiusChunks;
        int maxBlockZ = (spawn.getZ() >> 4) + radiusChunks;
        return predictAndVerifyInRect(level, minBlockX * 16, minBlockZ * 16, maxBlockX * 16, maxBlockZ * 16);
    }

    public static List<StructureInfo> predictAndVerifyInRect(ServerLevel level,
                                                             int minBlockX, int minBlockZ,
                                                             int maxBlockX, int maxBlockZ) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            return List.of();
        }

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.structurePredictionEnabled()) {
            return List.of();
        }
        StructureFileStorage.ensurePolicy(level, policyHash(cfg));

        final long tAll0 = CACHE_DEBUG ? System.nanoTime() : 0L;
        final int beforeCount = CACHE_DEBUG
                ? StructureFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, StructureFileStorage.SOURCE_PREDICTED).size()
                : 0;

        int cminx = Math.floorDiv(minBlockX, 16);
        int cminz = Math.floorDiv(minBlockZ, 16);
        int cmaxx = Math.floorDiv(maxBlockX, 16);
        int cmaxz = Math.floorDiv(maxBlockZ, 16);

        int windowsClaimed = 0;
        int predictedTotal = 0;
        int verifiedTotal = 0;

        if (StructureFileStorage.claimScanWindow(level, cminx, cminz, cmaxx, cmaxz)) {
            windowsClaimed++;
            try {
                List<StructureInfo> predicted = StructurePredictor.predictStructuresInRect(
                        level,
                        cminx, cminz, cmaxx, cmaxz,
                        cfg.biomePrefilter(),
                        cfg.structureWhitelist(),
                        cfg.structureBlacklist()
                );
                List<StructureInfo> verified = StructureVerificationService.verifyPredictedStructures(level, predicted);
                predictedTotal += predicted != null ? predicted.size() : 0;
                verifiedTotal += verified != null ? verified.size() : 0;
                if (verified != null && !verified.isEmpty()) {
                    StructureFileStorage.addStructures(level, verified, StructureFileStorage.SOURCE_PREDICTED);
                    MapPatchService.publishStructures(level, verified);
                }
                StructureFileStorage.markScanWindowDone(level, cminx, cminz, cmaxx, cmaxz);
            } catch (Throwable t) {
                StructureFileStorage.releaseScanWindow(level, cminx, cminz, cmaxx, cmaxz);
                LOGGER.warn("StructureIndexService: scan window failed rect=[{},{}..{},{}]", cminx, cminz, cmaxx, cmaxz, t);
            }
        }

        List<StructureInfo> out = StructureFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, StructureFileStorage.SOURCE_PREDICTED);
        if (CACHE_DEBUG) {
            int afterCount = out.size();
            long ms = (System.nanoTime() - tAll0) / 1_000_000L;
            LOGGER.info("StructureIndexService.predictAndVerifyInRect rect=[{},{}..{},{}] windowsClaimed={} predicted={} verified={} cachedBefore={} cachedAfter={} timeMs={}",
                    minBlockX, minBlockZ, maxBlockX, maxBlockZ,
                    windowsClaimed,
                    predictedTotal, verifiedTotal,
                    beforeCount, afterCount,
                    ms);
        }
        return out;
    }

    private static String policyHash(ModConfig cfg) {
        if (cfg == null) return "";
        List<String> wl = cfg.structureWhitelist() != null ? new ArrayList<>(cfg.structureWhitelist()) : new ArrayList<>();
        List<String> bl = cfg.structureBlacklist() != null ? new ArrayList<>(cfg.structureBlacklist()) : new ArrayList<>();
        Collections.sort(wl);
        Collections.sort(bl);
        String raw = "biomePrefilter=" + cfg.biomePrefilter()
                + "|whitelist=" + String.join(",", wl)
                + "|blacklist=" + String.join(",", bl);
        raw += "|scanWindowVersion=2";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}

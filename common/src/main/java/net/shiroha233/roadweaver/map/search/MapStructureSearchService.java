package net.shiroha233.roadweaver.map.search;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MapStructureSearchService {
    public static final int MAX_QUERY_LENGTH = 64;
    public static final int MAX_RESULTS = 50;
    private static final long MIN_REQUEST_INTERVAL_MS = 200L;
    private static final long STALE_REQUEST_AGE_MS = 10 * 60 * 1000L;
    private static final Pattern COORDINATES = Pattern.compile("^\\s*(-?\\d+)\\s*[, ]\\s*(-?\\d+)\\s*$");
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    private MapStructureSearchService() {}

    public static boolean tryBeginRequest(UUID playerId) {
        if (playerId == null || !IN_FLIGHT.add(playerId)) return false;
        long now = System.currentTimeMillis();
        Long previous = LAST_REQUEST.put(playerId, now);
        if (previous != null && now - previous < MIN_REQUEST_INTERVAL_MS) {
            IN_FLIGHT.remove(playerId);
            return false;
        }
        if (LAST_REQUEST.size() > 1024) {
            LAST_REQUEST.entrySet().removeIf(entry -> now - entry.getValue() > STALE_REQUEST_AGE_MS);
        }
        return true;
    }

    public static void finishRequest(UUID playerId) {
        if (playerId != null) IN_FLIGHT.remove(playerId);
    }

    public static List<MapSearchResult> search(ServerLevel level, String rawQuery) {
        if (level == null || rawQuery == null) return List.of();
        String query = rawQuery.trim();
        if (query.isEmpty() || query.length() > MAX_QUERY_LENGTH) return List.of();

        List<MapSearchResult> candidates = collectCandidates(level);
        Matcher coordinateMatcher = COORDINATES.matcher(query);
        if (coordinateMatcher.matches()) {
            int x;
            int z;
            try {
                x = Integer.parseInt(coordinateMatcher.group(1));
                z = Integer.parseInt(coordinateMatcher.group(2));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
            return candidates.stream()
                    .sorted(Comparator.comparingLong(result -> distanceSquared(result.pos(), x, z)))
                    .limit(MAX_RESULTS)
                    .toList();
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        ArrayList<ScoredResult> scored = new ArrayList<>();
        for (MapSearchResult result : candidates) {
            String id = result.structureId().toLowerCase(Locale.ROOT);
            int score = matchScore(id, normalized);
            if (score >= 0) scored.add(new ScoredResult(result, score));
        }
        scored.sort(Comparator.comparingInt(ScoredResult::score)
                .thenComparing(result -> result.result().structureId())
                .thenComparingInt(result -> result.result().pos().getX())
                .thenComparingInt(result -> result.result().pos().getZ()));
        return scored.stream().limit(MAX_RESULTS).map(ScoredResult::result).toList();
    }

    private static List<MapSearchResult> collectCandidates(ServerLevel level) {
        ModConfig config = ConfigService.get();
        boolean allowPredicted = config != null
                && config.structurePrediction().enabled()
                && Level.OVERWORLD.equals(level.dimension());

        StructureFileStorage.StructureSnapshot storageSnapshot = StructureFileStorage.getStructureSnapshot(level);
        StructureLocationData data = storageSnapshot.locations();
        Map<Long, MapSearchResult> byPosition = new HashMap<>();
        if (data != null) {
            if (data.structureInfos() != null) {
                for (StructureInfo info : data.structureInfos()) {
                    addCandidate(storageSnapshot, byPosition, info, allowPredicted);
                }
            }
            if (data.structureLocations() != null) {
                for (BlockPos pos : data.structureLocations()) {
                    if (pos == null) continue;
                    int source = storageSnapshot.sourceAt(pos);
                    if (!allowPredicted && source != MapStructureSource.MANUAL.id()) continue;
                    long key = positionKey(pos);
                    byPosition.putIfAbsent(key, new MapSearchResult(pos, "unknown", source));
                }
            }
        }
        return new ArrayList<>(byPosition.values());
    }

    private static void addCandidate(StructureFileStorage.StructureSnapshot storageSnapshot,
                                     Map<Long, MapSearchResult> byPosition,
                                     StructureInfo info,
                                     boolean allowPredicted) {
        if (info == null || info.pos() == null) return;
        int source = storageSnapshot.sourceAt(info.pos());
        if (!allowPredicted && source == MapStructureSource.PREDICTED.id()) return;
        BlockPos pos = new BlockPos(info.pos().getX(), 0, info.pos().getZ());
        long key = positionKey(pos);
        MapSearchResult next = new MapSearchResult(pos, info.structureId(), source);
        MapSearchResult previous = byPosition.get(key);
        if (previous == null || (previous.structureId().equals("unknown") && !next.structureId().equals("unknown"))) {
            byPosition.put(key, next);
        }
    }

    private static int matchScore(String value, String query) {
        if (value.equals(query)) return 0;
        if (value.startsWith(query)) return 1;
        if (value.contains(query)) return 2;
        return -1;
    }

    private static long distanceSquared(BlockPos pos, int x, int z) {
        long dx = (long) pos.getX() - x;
        long dz = (long) pos.getZ() - z;
        return dx * dx + dz * dz;
    }

    private static long positionKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
    }

    private record ScoredResult(MapSearchResult result, int score) {}
}

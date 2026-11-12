package net.shiroha233.roadweaver.client.map.query;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.client.map.cache.ClientMapNotes;
import net.shiroha233.roadweaver.map.search.MapSearchResult;
import net.shiroha233.roadweaver.map.search.MapStructureSearchService;
import net.shiroha233.roadweaver.network.platform.ClientNetBridge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图搜索查询状态机：负责输入防抖、请求超时判定，以及本地别名与服务器结果的合并。
 */
public final class MapSearchController {

    private static final long DEBOUNCE_MS = 250L;

    private static final long TIMEOUT_MS = 8_000L;

    private String query = "";

    private String pendingQuery = "";

    private String submittedQuery = "";

    private List<MapSearchResult> results = List.of();

    private boolean loading;

    private boolean failed;

    private int requestSeq;

    private long deadlineMs;

    private long startedAtMs;

    public String query() {
        return query;
    }

    public List<MapSearchResult> results() {
        return results;
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean hasFailed() {
        return failed;
    }

    public void onQueryChanged(String value) {
        query = value == null ? "" : value;
        pendingQuery = query.trim();
        failed = false;
        loading = false;
        startedAtMs = 0L;
        results = ClientMapNotes.searchAliases(pendingQuery, MapStructureSearchService.MAX_RESULTS);
        if (pendingQuery.isEmpty()) {
            deadlineMs = 0L;
            return;
        }
        deadlineMs = System.currentTimeMillis() + DEBOUNCE_MS;
    }

    public void tick(ResourceLocation dimensionId) {
        long now = System.currentTimeMillis();
        if (loading && startedAtMs > 0L && now - startedAtMs > TIMEOUT_MS) {
            loading = false;
            failed = true;
            startedAtMs = 0L;
            requestSeq++;
        }
        if (deadlineMs <= 0L || now < deadlineMs) {
            return;
        }
        submit(dimensionId);
    }

    public void submitNow(ResourceLocation dimensionId) {
        submit(dimensionId);
    }

    public void accept(int requestSeq, boolean success, List<MapSearchResult> serverResults) {
        if (requestSeq != this.requestSeq) {
            return;
        }
        if (!submittedQuery.equals(query.trim())) {
            return;
        }
        loading = false;
        startedAtMs = 0L;
        failed = !success;
        if (!success) {
            return;
        }
        Map<Long, MapSearchResult> merged = new LinkedHashMap<>();
        for (MapSearchResult local : ClientMapNotes.searchAliases(query, MapStructureSearchService.MAX_RESULTS)) {
            merged.put(resultKey(local.pos()), local);
        }
        if (serverResults != null) {
            for (MapSearchResult result : serverResults) {
                if (result == null || result.pos() == null) {
                    continue;
                }
                if (!merged.containsKey(resultKey(result.pos()))
                        && merged.size() >= MapStructureSearchService.MAX_RESULTS) {
                    break;
                }
                merged.put(resultKey(result.pos()), result);
            }
        }
        results = List.copyOf(merged.values());
    }

    private void submit(ResourceLocation dimensionId) {
        String currentQuery = query.trim();
        deadlineMs = 0L;
        if (currentQuery.isEmpty() || dimensionId == null) {
            loading = false;
            results = List.of();
            return;
        }
        submittedQuery = currentQuery;
        loading = true;
        failed = false;
        startedAtMs = System.currentTimeMillis();
        int seq = ++requestSeq;
        ClientNetBridge.requestSearch(seq, dimensionId, currentQuery);
    }

    private static long resultKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
    }
}

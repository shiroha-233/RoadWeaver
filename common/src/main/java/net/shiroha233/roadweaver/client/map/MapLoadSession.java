package net.shiroha233.roadweaver.client.map;

import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.map.data.MapSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.shiroha233.roadweaver.core.model.MapLoadPhase;
import net.shiroha233.roadweaver.core.model.RequestRect;

/**
 * 地图加载会话。
 */
public final class MapLoadSession {
    private final int requestSeq;
    private final Identifier dimensionId;
    private final int totalResponses;
    private final long startedAtMs;
    private final List<ResponseRequest> requests;
    private final HashSet<ResponseMarker> receivedResponses = new HashSet<>();

    private int completedResponses;
    private boolean hasData;
    private MapLoadPhase lastPhase;

    public MapLoadSession(int requestSeq, Identifier dimensionId, List<ResponseRequest> requests) {
        this.requestSeq = requestSeq;
        this.dimensionId = dimensionId;
        this.requests = List.copyOf(requests != null ? requests : List.of());
        this.totalResponses = Math.max(1, this.requests.size());
        this.startedAtMs = System.currentTimeMillis();
    }

    public boolean accepts(int requestSeq, Identifier dimensionId) {
        return this.requestSeq == requestSeq && Objects.equals(this.dimensionId, dimensionId);
    }

    public ResponseRequest requestAt(int responseIndex) {
        if (responseIndex < 0 || responseIndex >= requests.size()) return null;
        return requests.get(responseIndex);
    }

    public void markReceived(MapLoadPhase phase, int responseIndex, MapSnapshot snapshot) {
        ResponseMarker marker = new ResponseMarker(phase, responseIndex);
        if (receivedResponses.add(marker)) {
            completedResponses = Math.min(totalResponses, completedResponses + 1);
            if (phase != null) {
                lastPhase = phase;
            }
        }
        if (snapshot != null && (!snapshot.structures().isEmpty() || !snapshot.connections().isEmpty() || !snapshot.roadPolylines().isEmpty())) {
            hasData = true;
        }
    }

    public boolean hasData() {
        return hasData;
    }

    public boolean isComplete() {
        return completedResponses >= totalResponses;
    }

    public MapLoadPhase lastPhase() {
        return lastPhase;
    }

    public int completedResponses() {
        return completedResponses;
    }

    public int totalResponses() {
        return totalResponses;
    }

    public long elapsedMs() {
        return Math.max(0L, System.currentTimeMillis() - startedAtMs);
    }

    private record ResponseMarker(MapLoadPhase phase, int responseIndex) {}

    public record ResponseRequest(MapLoadPhase phase, RequestRect rect) {
        public static List<ResponseRequest> fromRects(List<RequestRect> structures,
                                                      List<RequestRect> roads,
                                                      List<RequestRect> connections) {
            ArrayList<ResponseRequest> out = new ArrayList<>();
            append(out, MapLoadPhase.STRUCTURES, structures);
            append(out, MapLoadPhase.ROADS, roads);
            append(out, MapLoadPhase.CONNECTIONS, connections);
            return out;
        }

        private static void append(ArrayList<ResponseRequest> out,
                                   MapLoadPhase phase,
                                   List<RequestRect> rects) {
            if (rects == null || rects.isEmpty()) return;
            for (RequestRect rect : rects) {
                if (rect != null) out.add(new ResponseRequest(phase, rect));
            }
        }
    }
}

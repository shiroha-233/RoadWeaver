package net.shiroha233.roadweaver.client.map.load;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.MapLoadSession;
import net.shiroha233.roadweaver.core.model.MapLoadPhase;
import net.shiroha233.roadweaver.core.model.RequestRect;
import net.shiroha233.roadweaver.map.data.MapDataCollector;
import net.shiroha233.roadweaver.map.data.MapSnapshot;
import net.shiroha233.roadweaver.network.platform.ClientNetBridge;
import net.shiroha233.roadweaver.runtime.ComputeService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MapSnapshotRequests {

    private MapSnapshotRequests() {
    }

    public interface SnapshotPartReceiver {
        void accept(int requestSeq, Identifier dimensionId, MapLoadPhase phase, int responseIndex, MapSnapshot snapshot);
    }

    public static void dispatchRemote(int requestSeq,
                                      Identifier dimensionId,
                                      List<MapLoadSession.ResponseRequest> requests) {
        for (int responseIndex = 0; responseIndex < requests.size(); responseIndex++) {
            MapLoadSession.ResponseRequest request = requests.get(responseIndex);
            RequestRect rect = request.rect();
            ClientNetBridge.requestSnapshot(requestSeq, dimensionId, request.phase(), responseIndex,
                    rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
        }
    }

    public static void scheduleSingleplayer(Minecraft minecraft,
                                           ServerLevel level,
                                           int requestSeq,
                                           List<MapLoadSession.ResponseRequest> requests,
                                           SnapshotPartReceiver receiver) {
        Identifier dimensionId = level.dimension().identifier();
        for (int responseIndex = 0; responseIndex < requests.size(); responseIndex++) {
            MapLoadSession.ResponseRequest request = requests.get(responseIndex);
            RequestRect rect = request.rect();
            schedulePhase(minecraft, level, requestSeq, dimensionId, request.phase(), responseIndex, rect, receiver);
        }
    }

    public static MapSnapshot buildForPhase(ServerLevel level, MapLoadPhase phase, RequestRect rect) {
        return switch (phase) {
            case STRUCTURES -> MapDataCollector.buildStructuresSnapshot(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
            case ROADS -> MapDataCollector.buildRoadsSnapshot(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
            case CONNECTIONS -> MapDataCollector.buildConnectionsSnapshot(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
        };
    }

    private static void schedulePhase(Minecraft minecraft,
                                      ServerLevel level,
                                      int requestSeq,
                                      Identifier dimensionId,
                                      MapLoadPhase phase,
                                      int responseIndex,
                                      RequestRect rect,
                                      SnapshotPartReceiver receiver) {
        CompletableFuture
                .supplyAsync(() -> buildForPhase(level, phase, rect), ComputeService.mapExecutor())
                .thenAccept(snapshot -> minecraft.execute(
                        () -> receiver.accept(requestSeq, dimensionId, phase, responseIndex, snapshot)));
    }
}

package net.shiroha233.roadweaver.network.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.api.RoadNetworkApi;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapAutomaticPlanningSamplingCache;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.map.search.MapStructureSearchService;
import net.shiroha233.roadweaver.network.MapNetworkPayloads;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MapNetworkFabric {
    private MapNetworkFabric() {}

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.REQ_RECT, (server, player, handler, buf, responseSender) -> {
            MapNetworkPayloads.MapRequestRectPayload request = MapNetworkPayloads.MapRequestRectPayload.CODEC.decode(buf);
            if (!MapAccessService.canOpenMap(player)) { server.execute(() -> syncMapAccess(player)); return; }
            CompletableFuture.supplyAsync(() -> {
                var level = player.serverLevel();
                return new MapNetworkPayloads.MapSnapshotPayload(request.requestSeq(), level.dimension().location(), request.phase(), request.responseIndex(),
                        buildSnapshot(level, request.phase(), request.minX(), request.minZ(), request.maxX(), request.maxZ()), AutomaticPlanningSamplingActivities.snapshot(level));
            }, ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP)).thenAccept(reply -> server.execute(() -> { if (!player.isRemoved()) send(player, MapNetworkPayloads.SNAP, MapNetworkPayloads.MapSnapshotPayload.CODEC, reply); }));
        });
        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.TP_REQ, (server, player, handler, buf, responseSender) -> { var request = MapNetworkPayloads.MapTeleportPayload.CODEC.decode(buf); server.execute(() -> handleTeleport(request, player, server)); });
        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.MAN_REQ, (server, player, handler, buf, responseSender) -> { var request = MapNetworkPayloads.MapManualConnectPayload.CODEC.decode(buf); server.execute(() -> { if (!player.hasPermissions(2)) player.displayClientMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"), true); else if (Level.OVERWORLD.equals(player.serverLevel().dimension())) RoadNetworkApi.ensureConnection(player.serverLevel(), request.from(), request.to()); }); });
        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SEARCH_REQ, (server, player, handler, buf, responseSender) -> {
            var request = MapNetworkPayloads.MapSearchRequestPayload.CODEC.decode(buf);
            if (!MapAccessService.canOpenMap(player) || !player.serverLevel().dimension().location().equals(request.dimension()) || !MapStructureSearchService.tryBeginRequest(player.getUUID())) { server.execute(() -> send(player, MapNetworkPayloads.SEARCH_RESP, MapNetworkPayloads.MapSearchResponsePayload.CODEC, new MapNetworkPayloads.MapSearchResponsePayload(request.requestSeq(), request.dimension(), false, List.of()))); return; }
            CompletableFuture.supplyAsync(() -> MapStructureSearchService.search(player.serverLevel(), request.query()), ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP)).handle((results, failure) -> { MapStructureSearchService.finishRequest(player.getUUID()); return new MapNetworkPayloads.MapSearchResponsePayload(request.requestSeq(), request.dimension(), failure == null, failure == null ? results : List.of()); }).thenAccept(reply -> server.execute(() -> { if (!player.isRemoved()) send(player, MapNetworkPayloads.SEARCH_RESP, MapNetworkPayloads.MapSearchResponsePayload.CODEC, reply); }));
        });
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SNAP, (client, handler, buf, responseSender) -> { var payload = MapNetworkPayloads.MapSnapshotPayload.CODEC.decode(buf); client.execute(() -> { MapAutomaticPlanningSamplingCache.replace(payload.dimension(), payload.automaticPlanningSamplingBounds()); if (client.screen instanceof RoadMapScreen screen) screen.acceptSnapshotPart(payload.requestSeq(), payload.dimension(), payload.phase(), payload.responseIndex(), payload.snapshot()); }); });
        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.PATCH, (client, handler, buf, responseSender) -> { var payload = MapNetworkPayloads.MapPatchPayload.CODEC.decode(buf); client.execute(() -> { if (client.screen instanceof RoadMapScreen screen) screen.acceptPatch(payload.dimension(), payload.patch()); else MapSnapshotCache.applyPatch(payload.dimension(), payload.patch()); }); });
        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.TP_ACK, (client, handler, buf, responseSender) -> { var payload = MapNetworkPayloads.MapTeleportAckPayload.CODEC.decode(buf); client.execute(() -> { if (client.player != null) client.player.displayClientMessage(payload.success() ? Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x(), payload.y(), payload.z()) : Component.translatable("gui.roadweaver.map.teleport.denied"), true); }); });
        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.ACCESS_SYNC, (client, handler, buf, responseSender) -> { var payload = MapNetworkPayloads.MapAccessSyncPayload.CODEC.decode(buf); client.execute(() -> ClientMapAccessGuard.applyServerState(client, payload.allowed())); });
        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.AUTO_PLANNING_SAMPLING, (client, handler, buf, responseSender) -> { var payload = MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.CODEC.decode(buf); client.execute(() -> MapAutomaticPlanningSamplingCache.replace(payload.dimension(), payload.bounds())); });
        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SEARCH_RESP, (client, handler, buf, responseSender) -> { var payload = MapNetworkPayloads.MapSearchResponsePayload.CODEC.decode(buf); client.execute(() -> { if (client.screen instanceof RoadMapScreen screen) screen.acceptSearchResults(payload.requestSeq(), payload.dimension(), payload.success(), payload.results()); }); });
    }

    private static MapSnapshot buildSnapshot(net.minecraft.server.level.ServerLevel level, MapLoadPhase phase, int minX, int minZ, int maxX, int maxZ) { return switch (phase) { case STRUCTURES -> MapDataCollector.buildStructuresSnapshot(level, minX, minZ, maxX, maxZ); case ROADS -> MapDataCollector.buildRoadsSnapshot(level, minX, minZ, maxX, maxZ); case CONNECTIONS -> MapDataCollector.buildConnectionsSnapshot(level, minX, minZ, maxX, maxZ); }; }
    private static void handleTeleport(MapNetworkPayloads.MapTeleportPayload request, ServerPlayer player, net.minecraft.server.MinecraftServer server) { if (!player.isCreative() && !player.hasPermissions(2)) { send(player, MapNetworkPayloads.TP_ACK, MapNetworkPayloads.MapTeleportAckPayload.CODEC, new MapNetworkPayloads.MapTeleportAckPayload(false, 0, 0, 0)); return; } var level = player.serverLevel(); int x = request.x(), z = request.z(); level.getChunkSource().getChunkFuture(x >> 4, z >> 4, ChunkStatus.FULL, true).thenAccept(ignored -> server.execute(() -> { if (player.isRemoved() || player.hasDisconnected()) return; int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z); y = y <= level.getMinBuildHeight() ? level.getSeaLevel() + 1 : y + 1; player.teleportTo(level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot()); send(player, MapNetworkPayloads.TP_ACK, MapNetworkPayloads.MapTeleportAckPayload.CODEC, new MapNetworkPayloads.MapTeleportAckPayload(true, x, y, z)); })); }
    private static <T> void send(ServerPlayer player, ResourceLocation id, MapNetworkPayloads.Codec<T> codec, T payload) { FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer()); codec.encode(out, payload); ServerPlayNetworking.send(player, id, out); }
    private static <T> void sendClient(ResourceLocation id, MapNetworkPayloads.Codec<T> codec, T payload) { FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer()); codec.encode(out, payload); ClientPlayNetworking.send(id, out); }
    public static void requestSnapshot(int requestSeq, ResourceLocation dimensionId, MapLoadPhase phase, int responseIndex, int minX, int minZ, int maxX, int maxZ) { sendClient(MapNetworkPayloads.REQ_RECT, MapNetworkPayloads.MapRequestRectPayload.CODEC, new MapNetworkPayloads.MapRequestRectPayload(requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ)); }
    public static void requestTeleport(int x, int y, int z) { sendClient(MapNetworkPayloads.TP_REQ, MapNetworkPayloads.MapTeleportPayload.CODEC, new MapNetworkPayloads.MapTeleportPayload(x, y, z)); }
    public static void requestManualConnect(int ax, int az, int bx, int bz) { sendClient(MapNetworkPayloads.MAN_REQ, MapNetworkPayloads.MapManualConnectPayload.CODEC, new MapNetworkPayloads.MapManualConnectPayload(new BlockPos(ax, 0, az), new BlockPos(bx, 0, bz))); }
    public static void requestSearch(int requestSeq, ResourceLocation dimensionId, String query) { sendClient(MapNetworkPayloads.SEARCH_REQ, MapNetworkPayloads.MapSearchRequestPayload.CODEC, new MapNetworkPayloads.MapSearchRequestPayload(requestSeq, dimensionId, query)); }
    public static void broadcastPatch(ServerPlayer player, ResourceLocation dimensionId, MapSnapshotPatch patch) { if (player != null && dimensionId != null && patch != null && !patch.isEmpty()) send(player, MapNetworkPayloads.PATCH, MapNetworkPayloads.MapPatchPayload.CODEC, new MapNetworkPayloads.MapPatchPayload(dimensionId, patch)); }
    public static void broadcastAutomaticPlanningSampling(ServerPlayer player, ResourceLocation dimensionId, List<net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds> bounds) { if (player != null && dimensionId != null) send(player, MapNetworkPayloads.AUTO_PLANNING_SAMPLING, MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.CODEC, new MapNetworkPayloads.MapAutomaticPlanningSamplingPayload(dimensionId, bounds)); }
    public static void syncMapAccess(ServerPlayer player) { if (player != null) send(player, MapNetworkPayloads.ACCESS_SYNC, MapNetworkPayloads.MapAccessSyncPayload.CODEC, new MapNetworkPayloads.MapAccessSyncPayload(MapAccessService.canOpenMap(player))); }
}

/* 文件职责：实现 Fabric 平台道路地图的请求、快照与增量状态网络通信。 */
package net.shiroha233.roadweaver.network.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.api.RoadNetworkApi;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.client.map.data.MapAutomaticPlanningSamplingCache;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.network.MapNetworkPayloads;
import net.shiroha233.roadweaver.network.RawPathDebugPayloads;
import net.shiroha233.roadweaver.planning.path.RawPathDebugService;
import net.shiroha233.roadweaver.client.debug.RawPathDebugOverlay;
import net.shiroha233.roadweaver.map.search.MapStructureSearchService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Fabric 平台网络通信实现。 */
public final class MapNetworkFabric {
    private static boolean payloadTypesRegistered;

    private MapNetworkFabric() {}

    private static synchronized void registerPayloadTypes() {
        if (payloadTypesRegistered) return;

        PayloadTypeRegistry.playC2S().register(MapNetworkPayloads.REQ_RECT, MapNetworkPayloads.MapRequestRectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapNetworkPayloads.TP_REQ, MapNetworkPayloads.MapTeleportPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapNetworkPayloads.MAN_REQ, MapNetworkPayloads.MapManualConnectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapNetworkPayloads.SEARCH_REQ, MapNetworkPayloads.MapSearchRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RawPathDebugPayloads.REQUEST, RawPathDebugPayloads.Request.CODEC);

        PayloadTypeRegistry.playS2C().register(MapNetworkPayloads.SNAP, MapNetworkPayloads.MapSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapNetworkPayloads.PATCH, MapNetworkPayloads.MapPatchPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapNetworkPayloads.AUTO_PLANNING_SAMPLING, MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapNetworkPayloads.TP_ACK, MapNetworkPayloads.MapTeleportAckPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapNetworkPayloads.ACCESS_SYNC, MapNetworkPayloads.MapAccessSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapNetworkPayloads.SEARCH_RESP, MapNetworkPayloads.MapSearchResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RawPathDebugPayloads.RESPONSE, RawPathDebugPayloads.Response.CODEC);

        payloadTypesRegistered = true;
    }

    public static void registerServerReceivers() {
        registerPayloadTypes();
        registerRawPathRequest();

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.REQ_RECT, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!MapAccessService.canOpenMap(player)) {
                context.server().execute(() -> syncMapAccess(player));
                return;
            }

            CompletableFuture
                    .supplyAsync(() -> {
                        var level = player.serverLevel();
                        MapSnapshot snapshot = buildSnapshot(level, payload.phase(), payload.minX(), payload.minZ(), payload.maxX(), payload.maxZ());
                        return new MapNetworkPayloads.MapSnapshotPayload(
                                payload.requestSeq(),
                                level.dimension().location(),
                                payload.phase(),
                                payload.responseIndex(),
                                snapshot,
                                AutomaticPlanningSamplingActivities.snapshot(level)
                        );
                    }, ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP))
                    .thenAccept(reply -> context.server().execute(() -> {
                        if (!player.isRemoved()) ServerPlayNetworking.send(player, reply);
                    }));
        });

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.TP_REQ, (payload, context) ->
                context.server().execute(() -> handleTeleport(payload, context.player(), context.server()))
        );

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.MAN_REQ, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!player.hasPermissions(2)) {
                        player.displayClientMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"), true);
                        return;
                    }
                    if (!Level.OVERWORLD.equals(player.serverLevel().dimension())) return;
                    RoadNetworkApi.ensureConnection(player.serverLevel(), payload.from(), payload.to());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SEARCH_REQ, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!MapAccessService.canOpenMap(player)) {
                context.server().execute(() -> ServerPlayNetworking.send(player,
                        new MapNetworkPayloads.MapSearchResponsePayload(payload.requestSeq(), payload.dimension(), false, java.util.List.of())));
                return;
            }
            var level = player.serverLevel();
            if (!level.dimension().location().equals(payload.dimension())
                    || !MapStructureSearchService.tryBeginRequest(player.getUUID())) {
                context.server().execute(() -> ServerPlayNetworking.send(player,
                        new MapNetworkPayloads.MapSearchResponsePayload(payload.requestSeq(), payload.dimension(), false, java.util.List.of())));
                return;
            }
            try {
                CompletableFuture
                        .supplyAsync(() -> MapStructureSearchService.search(level, payload.query()),
                                ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP))
                        .handle((results, failure) -> {
                            MapStructureSearchService.finishRequest(player.getUUID());
                            return new MapNetworkPayloads.MapSearchResponsePayload(
                                    payload.requestSeq(), payload.dimension(), failure == null,
                                    failure == null ? results : java.util.List.of());
                        })
                        .thenAccept(reply -> context.server().execute(() -> {
                            if (!player.isRemoved()) ServerPlayNetworking.send(player, reply);
                        }));
            } catch (RuntimeException submissionFailure) {
                MapStructureSearchService.finishRequest(player.getUUID());
                context.server().execute(() -> ServerPlayNetworking.send(player,
                        new MapNetworkPayloads.MapSearchResponsePayload(payload.requestSeq(), payload.dimension(), false, java.util.List.of())));
            }
        });
    }

    public static void registerClientReceivers() {
        registerPayloadTypes();

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SNAP, (payload, context) ->
                context.client().execute(() -> {
                    MapAutomaticPlanningSamplingCache.replace(
                            payload.dimension(),
                            payload.automaticPlanningSamplingBounds());
                    if (context.client().screen instanceof RoadMapScreen screen) {
                        screen.acceptSnapshotPart(payload.requestSeq(), payload.dimension(), payload.phase(), payload.responseIndex(), payload.snapshot());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.PATCH, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof RoadMapScreen screen) {
                        screen.acceptPatch(payload.dimension(), payload.patch());
                    } else {
                        MapSnapshotCache.applyPatch(payload.dimension(), payload.patch());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.TP_ACK, (payload, context) ->
                context.client().execute(() -> {
                    if (context.player() == null) return;
                    if (payload.success()) {
                        context.player().displayClientMessage(
                                Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x(), payload.y(), payload.z()), true);
                    } else {
                        context.player().displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.denied"), true);
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.ACCESS_SYNC, (payload, context) ->
                context.client().execute(() -> ClientMapAccessGuard.applyServerState(context.client(), payload.allowed()))
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.AUTO_PLANNING_SAMPLING, (payload, context) ->
                context.client().execute(() -> MapAutomaticPlanningSamplingCache.replace(
                        payload.dimension(),
                        payload.bounds()))
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SEARCH_RESP, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof RoadMapScreen screen) {
                        screen.acceptSearchResults(payload.requestSeq(), payload.dimension(), payload.success(), payload.results());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(RawPathDebugPayloads.RESPONSE, (payload, context) ->
                context.client().execute(() -> RawPathDebugOverlay.accept(payload)));
    }

    private static void registerRawPathRequest() {
        ServerPlayNetworking.registerGlobalReceiver(RawPathDebugPayloads.REQUEST, (payload, context) -> {
            ServerPlayer player = context.player();
            var level = player.serverLevel();
            if (!player.hasPermissions(2)) {
                context.server().execute(() -> player.displayClientMessage(
                        Component.translatable("debug.roadweaver.raw_paths.denied"), true));
                return;
            }
            if (!level.dimension().location().equals(payload.dimension())) return;
            int x = player.getBlockX(), z = player.getBlockZ();
            CompletableFuture.supplyAsync(() -> new RawPathDebugPayloads.Response(
                            payload.sequence(), level.dimension().location(), RawPathDebugService.enabled(),
                            RawPathDebugService.nearby(level, x, z)),
                    ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP))
                    .thenAccept(reply -> context.server().execute(() -> {
                        if (!player.isRemoved() && player.serverLevel() == level) ServerPlayNetworking.send(player, reply);
                    }));
        });
    }

    private static MapSnapshot buildSnapshot(net.minecraft.server.level.ServerLevel level,
                                             MapLoadPhase phase,
                                             int minX,
                                             int minZ,
                                             int maxX,
                                             int maxZ) {
        return switch (phase) {
            case STRUCTURES -> MapDataCollector.buildStructuresSnapshot(level, minX, minZ, maxX, maxZ);
            case ROADS -> MapDataCollector.buildRoadsSnapshot(level, minX, minZ, maxX, maxZ);
            case CONNECTIONS -> MapDataCollector.buildConnectionsSnapshot(level, minX, minZ, maxX, maxZ);
        };
    }

    private static void handleTeleport(MapNetworkPayloads.MapTeleportPayload payload,
                                       ServerPlayer player,
                                       net.minecraft.server.MinecraftServer server) {
        if (!player.isCreative() && !player.hasPermissions(2)) {
            ServerPlayNetworking.send(player, new MapNetworkPayloads.MapTeleportAckPayload(false, 0, 0, 0));
            return;
        }

        var level = player.serverLevel();
        int x = payload.x();
        int z = payload.z();
        level.getChunkSource().getChunkFuture(x >> 4, z >> 4, ChunkStatus.FULL, true)
                .thenAccept(ignored -> server.execute(() -> {
                    if (player.isRemoved() || player.hasDisconnected()) return;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    y = y <= level.getMinBuildHeight() ? level.getSeaLevel() + 1 : y + 1;
                    player.teleportTo(level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
                    ServerPlayNetworking.send(player, new MapNetworkPayloads.MapTeleportAckPayload(true, x, y, z));
                }));
    }

    public static void requestSnapshot(int requestSeq,
                                       ResourceLocation dimensionId,
                                       MapLoadPhase phase,
                                       int responseIndex,
                                       int minX,
                                       int minZ,
                                       int maxX,
                                       int maxZ) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapRequestRectPayload(
                requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ));
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapTeleportPayload(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapManualConnectPayload(
                new BlockPos(ax, 0, az), new BlockPos(bx, 0, bz)));
    }

    public static void requestSearch(int requestSeq, ResourceLocation dimensionId, String query) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapSearchRequestPayload(requestSeq, dimensionId, query));
    }

    public static void requestRawPathDebug(int requestSeq, ResourceLocation dimensionId) {
        ClientPlayNetworking.send(new RawPathDebugPayloads.Request(requestSeq, dimensionId));
    }

    public static void broadcastPatch(ServerPlayer player, ResourceLocation dimensionId, MapSnapshotPatch patch) {
        if (player == null || dimensionId == null || patch == null || patch.isEmpty()) return;
        ServerPlayNetworking.send(player, new MapNetworkPayloads.MapPatchPayload(dimensionId, patch));
    }

    public static void broadcastAutomaticPlanningSampling(ServerPlayer player,
                                                          ResourceLocation dimensionId,
                                                          List<AutomaticPlanningSamplingBounds> bounds) {
        if (player == null || dimensionId == null) return;
        ServerPlayNetworking.send(player,
                new MapNetworkPayloads.MapAutomaticPlanningSamplingPayload(dimensionId, bounds));
    }

    public static void syncMapAccess(ServerPlayer player) {
        if (player != null) {
            ServerPlayNetworking.send(player, new MapNetworkPayloads.MapAccessSyncPayload(MapAccessService.canOpenMap(player)));
        }
    }
}

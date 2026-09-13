/* 文件职责：实现 Forge 平台道路地图的请求、快照与增量状态网络通信。 */
package net.shiroha233.roadweaver.network.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.shiroha233.roadweaver.RoadWeaver;
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
import net.shiroha233.roadweaver.map.search.MapStructureSearchService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Forge 平台网络通信实现。 */
public final class MapNetworkForge {
    private MapNetworkForge() {}

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RoadWeaver.MOD_ID, "map"), () -> "1", "1"::equals, "1"::equals);
    private static int id;
    public static void register() {
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapRequestRectPayload.class, (m,b)->MapNetworkPayloads.MapRequestRectPayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapRequestRectPayload.CODEC.decode(b), MapNetworkForge::handleRequestRect, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapSnapshotPayload.class, (m,b)->MapNetworkPayloads.MapSnapshotPayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapSnapshotPayload.CODEC.decode(b), MapNetworkForge::handleSnapshot, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapPatchPayload.class, (m,b)->MapNetworkPayloads.MapPatchPayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapPatchPayload.CODEC.decode(b), MapNetworkForge::handlePatch, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapTeleportPayload.class, (m,b)->MapNetworkPayloads.MapTeleportPayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapTeleportPayload.CODEC.decode(b), MapNetworkForge::handleTeleportRequest, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapTeleportAckPayload.class, (m,b)->MapNetworkPayloads.MapTeleportAckPayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapTeleportAckPayload.CODEC.decode(b), MapNetworkForge::handleTeleportAck, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapManualConnectPayload.class, (m,b)->MapNetworkPayloads.MapManualConnectPayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapManualConnectPayload.CODEC.decode(b), MapNetworkForge::handleManualConnect, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapAccessSyncPayload.class, (m,b)->MapNetworkPayloads.MapAccessSyncPayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapAccessSyncPayload.CODEC.decode(b), MapNetworkForge::handleAccessSync, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapSearchRequestPayload.class, (m,b)->MapNetworkPayloads.MapSearchRequestPayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapSearchRequestPayload.CODEC.decode(b), MapNetworkForge::handleSearchRequest, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapSearchResponsePayload.class, (m,b)->MapNetworkPayloads.MapSearchResponsePayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapSearchResponsePayload.CODEC.decode(b), MapNetworkForge::handleSearchResponse, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.class, (m,b)->MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.CODEC.encode(b,m), b->MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.CODEC.decode(b), MapNetworkForge::handleAutomaticPlanningSampling, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void handleRequestRect(MapNetworkPayloads.MapRequestRectPayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null) return;
            if (!MapAccessService.canOpenMap(player)) {
                syncMapAccess(player);
                return;
            }

            CompletableFuture
                    .supplyAsync(() -> {
                        ServerLevel level = player.serverLevel();
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
                    .thenAccept(reply -> player.serverLevel().getServer().execute(() -> {
                        if (!player.isRemoved()) CHANNEL.sendTo(reply, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                    }));
        });
    }

    private static void handleSnapshot(MapNetworkPayloads.MapSnapshotPayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            MapAutomaticPlanningSamplingCache.replace(
                    payload.dimension(),
                    payload.automaticPlanningSamplingBounds());
            if (minecraft.screen instanceof RoadMapScreen screen) {
                screen.acceptSnapshotPart(payload.requestSeq(), payload.dimension(), payload.phase(), payload.responseIndex(), payload.snapshot());
            }
        });
    }

    private static void handlePatch(MapNetworkPayloads.MapPatchPayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof RoadMapScreen screen) {
                screen.acceptPatch(payload.dimension(), payload.patch());
            } else {
                MapSnapshotCache.applyPatch(payload.dimension(), payload.patch());
            }
        });
    }

    private static void handleTeleportRequest(MapNetworkPayloads.MapTeleportPayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null) return;
            if (!player.isCreative() && !player.hasPermissions(2)) {
                CHANNEL.sendTo(new MapNetworkPayloads.MapTeleportAckPayload(false, 0, 0, 0), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                return;
            }

            ServerLevel level = player.serverLevel();
            int x = payload.x();
            int z = payload.z();
            level.getChunkSource().getChunkFuture(x >> 4, z >> 4, ChunkStatus.FULL, true)
                    .thenAccept(ignored -> level.getServer().execute(() -> {
                        if (player.isRemoved() || player.hasDisconnected()) return;
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        y = y <= level.getMinBuildHeight() ? level.getSeaLevel() + 1 : y + 1;
                        player.teleportTo(level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
                        CHANNEL.sendTo(new MapNetworkPayloads.MapTeleportAckPayload(true, x, y, z), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                    }));
        });
    }

    private static void handleTeleportAck(MapNetworkPayloads.MapTeleportAckPayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            if (payload.success()) {
                player.displayClientMessage(
                        Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x(), payload.y(), payload.z()), true);
            } else {
                player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.denied"), true);
            }
        });
    }

    private static void handleAccessSync(MapNetworkPayloads.MapAccessSyncPayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> ClientMapAccessGuard.applyServerState(Minecraft.getInstance(), payload.allowed()));
    }

    private static void handleAutomaticPlanningSampling(
            MapNetworkPayloads.MapAutomaticPlanningSamplingPayload payload,
            Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> MapAutomaticPlanningSamplingCache.replace(
                payload.dimension(),
                payload.bounds()));
    }

    private static void handleSearchRequest(MapNetworkPayloads.MapSearchRequestPayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null) return;
            if (!MapAccessService.canOpenMap(player)) {
                CHANNEL.sendTo(new MapNetworkPayloads.MapSearchResponsePayload(
                        payload.requestSeq(), payload.dimension(), false, java.util.List.of()),
                        player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!level.dimension().location().equals(payload.dimension())
                    || !MapStructureSearchService.tryBeginRequest(player.getUUID())) {
                CHANNEL.sendTo(new MapNetworkPayloads.MapSearchResponsePayload(
                        payload.requestSeq(), payload.dimension(), false, java.util.List.of()),
                        player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
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
                        .thenAccept(reply -> level.getServer().execute(() -> {
                            if (!player.isRemoved()) CHANNEL.sendTo(reply, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                        }));
            } catch (RuntimeException submissionFailure) {
                MapStructureSearchService.finishRequest(player.getUUID());
                CHANNEL.sendTo(new MapNetworkPayloads.MapSearchResponsePayload(
                        payload.requestSeq(), payload.dimension(), false, java.util.List.of()),
                        player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        });
    }

    private static void handleSearchResponse(MapNetworkPayloads.MapSearchResponsePayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof RoadMapScreen screen) {
                screen.acceptSearchResults(payload.requestSeq(), payload.dimension(), payload.success(), payload.results());
            }
        });
    }

    private static void handleManualConnect(MapNetworkPayloads.MapManualConnectPayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context c = context.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"), true);
                return;
            }
            if (!Level.OVERWORLD.equals(player.serverLevel().dimension())) return;
            RoadNetworkApi.ensureConnection(player.serverLevel(), payload.from(), payload.to());
        });
    }

    private static MapSnapshot buildSnapshot(ServerLevel level,
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

    public static void requestSnapshot(int requestSeq,
                                       ResourceLocation dimensionId,
                                       MapLoadPhase phase,
                                       int responseIndex,
                                       int minX,
                                       int minZ,
                                       int maxX,
                                       int maxZ) {
        CHANNEL.sendToServer(new MapNetworkPayloads.MapRequestRectPayload(
                requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ));
    }

    public static void requestTeleport(int x, int y, int z) {
        CHANNEL.sendToServer(new MapNetworkPayloads.MapTeleportPayload(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        CHANNEL.sendToServer(new MapNetworkPayloads.MapManualConnectPayload(
                new BlockPos(ax, 0, az), new BlockPos(bx, 0, bz)));
    }

    public static void requestSearch(int requestSeq, ResourceLocation dimensionId, String query) {
        CHANNEL.sendToServer(new MapNetworkPayloads.MapSearchRequestPayload(requestSeq, dimensionId, query));
    }

    public static void broadcastPatch(ServerPlayer player, ResourceLocation dimensionId, MapSnapshotPatch patch) {
        if (player == null || dimensionId == null || patch == null || patch.isEmpty()) return;
        CHANNEL.sendTo(new MapNetworkPayloads.MapPatchPayload(dimensionId, patch), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void broadcastAutomaticPlanningSampling(ServerPlayer player,
                                                          ResourceLocation dimensionId,
                                                          List<AutomaticPlanningSamplingBounds> bounds) {
        if (player == null || dimensionId == null) return;
        CHANNEL.sendTo(new MapNetworkPayloads.MapAutomaticPlanningSamplingPayload(dimensionId, bounds), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void syncMapAccess(ServerPlayer player) {
        if (player != null) {
            CHANNEL.sendTo(new MapNetworkPayloads.MapAccessSyncPayload(MapAccessService.canOpenMap(player)), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }
}

package net.shiroha233.roadweaver.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.map.search.MapSearchResult;
import net.shiroha233.roadweaver.map.search.MapStructureSearchService;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;

import java.util.ArrayList;
import java.util.List;

public class MapNetworkPayloads {
    private static final int MAX_AUTOMATIC_PLANNING_SAMPLING_REGIONS = 256;

    public static final CustomPacketPayload.Type<MapRequestRectPayload> REQ_RECT = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_request_rect"));
    public static final CustomPacketPayload.Type<MapSnapshotPayload> SNAP = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_snapshot"));
    public static final CustomPacketPayload.Type<MapPatchPayload> PATCH = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_patch"));
    public static final CustomPacketPayload.Type<MapAutomaticPlanningSamplingPayload> AUTO_PLANNING_SAMPLING = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_automatic_planning_sampling"));
    public static final CustomPacketPayload.Type<MapTeleportPayload> TP_REQ = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_teleport"));
    public static final CustomPacketPayload.Type<MapTeleportAckPayload> TP_ACK = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_teleport_ack"));
    public static final CustomPacketPayload.Type<MapManualConnectPayload> MAN_REQ = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_manual_connect"));
    public static final CustomPacketPayload.Type<MapAccessSyncPayload> ACCESS_SYNC = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_access_sync"));
    public static final CustomPacketPayload.Type<MapSearchRequestPayload> SEARCH_REQ = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_search_request"));
    public static final CustomPacketPayload.Type<MapSearchResponsePayload> SEARCH_RESP = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_search_response"));

    public record MapRequestRectPayload(int requestSeq,
                                        ResourceLocation dimension,
                                        MapLoadPhase phase,
                                        int responseIndex,
                                        int minX,
                                        int minZ,
                                        int maxX,
                                        int maxZ) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapRequestRectPayload> CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeVarInt(val.requestSeq);
                buf.writeResourceLocation(val.dimension);
                buf.writeUtf(val.phase.name());
                buf.writeVarInt(val.responseIndex);
                buf.writeVarInt(val.minX);
                buf.writeVarInt(val.minZ);
                buf.writeVarInt(val.maxX);
                buf.writeVarInt(val.maxZ);
            },
            buf -> new MapRequestRectPayload(
                buf.readVarInt(),
                buf.readResourceLocation(),
                MapLoadPhase.valueOf(buf.readUtf()),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt()
            )
        );
        @Override public Type<MapRequestRectPayload> type() { return REQ_RECT; }
    }

    public record MapSnapshotPayload(int requestSeq,
                                     ResourceLocation dimension,
                                     MapLoadPhase phase,
                                     int responseIndex,
                                     MapSnapshot snapshot,
                                     List<AutomaticPlanningSamplingBounds> automaticPlanningSamplingBounds) implements CustomPacketPayload {
        public MapSnapshotPayload {
            automaticPlanningSamplingBounds = immutableAutomaticPlanningSamplingBounds(
                    automaticPlanningSamplingBounds);
        }

        public MapSnapshotPayload(int requestSeq,
                                  ResourceLocation dimension,
                                  MapLoadPhase phase,
                                  int responseIndex,
                                  MapSnapshot snapshot) {
            this(requestSeq, dimension, phase, responseIndex, snapshot, List.of());
        }

        public static final StreamCodec<FriendlyByteBuf, MapSnapshotPayload> CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeVarInt(val.requestSeq);
                buf.writeResourceLocation(val.dimension);
                buf.writeUtf(val.phase.name());
                buf.writeVarInt(val.responseIndex);
                MapSnapshotCodec.write(buf, val.snapshot);
                writeAutomaticPlanningSamplingBounds(buf, val.automaticPlanningSamplingBounds);
            },
            buf -> new MapSnapshotPayload(
                buf.readVarInt(),
                buf.readResourceLocation(),
                MapLoadPhase.valueOf(buf.readUtf()),
                buf.readVarInt(),
                MapSnapshotCodec.read(buf),
                readAutomaticPlanningSamplingBounds(buf)
            )
        );
        @Override public Type<MapSnapshotPayload> type() { return SNAP; }
    }

    public record MapPatchPayload(ResourceLocation dimension, MapSnapshotPatch patch) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapPatchPayload> CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeResourceLocation(val.dimension);
                MapSnapshotCodec.writePatch(buf, val.patch);
            },
            buf -> new MapPatchPayload(buf.readResourceLocation(), MapSnapshotCodec.readPatch(buf))
        );
        @Override public Type<MapPatchPayload> type() { return PATCH; }
    }

    public record MapAutomaticPlanningSamplingPayload(
            ResourceLocation dimension,
            List<AutomaticPlanningSamplingBounds> bounds) implements CustomPacketPayload {
        public MapAutomaticPlanningSamplingPayload {
            bounds = immutableAutomaticPlanningSamplingBounds(bounds);
        }

        public static final StreamCodec<FriendlyByteBuf, MapAutomaticPlanningSamplingPayload> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeResourceLocation(value.dimension);
                    writeAutomaticPlanningSamplingBounds(buf, value.bounds);
                },
                buf -> new MapAutomaticPlanningSamplingPayload(
                        buf.readResourceLocation(),
                        readAutomaticPlanningSamplingBounds(buf))
        );

        @Override public Type<MapAutomaticPlanningSamplingPayload> type() { return AUTO_PLANNING_SAMPLING; }
    }

    public record MapTeleportPayload(int x, int y, int z) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapTeleportPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MapTeleportPayload::x,
            ByteBufCodecs.VAR_INT, MapTeleportPayload::y,
            ByteBufCodecs.VAR_INT, MapTeleportPayload::z,
            MapTeleportPayload::new
        );
        @Override public Type<MapTeleportPayload> type() { return TP_REQ; }
    }

    public record MapTeleportAckPayload(boolean success, int x, int y, int z) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapTeleportAckPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MapTeleportAckPayload::success,
            ByteBufCodecs.VAR_INT, MapTeleportAckPayload::x,
            ByteBufCodecs.VAR_INT, MapTeleportAckPayload::y,
            ByteBufCodecs.VAR_INT, MapTeleportAckPayload::z,
            MapTeleportAckPayload::new
        );
        @Override public Type<MapTeleportAckPayload> type() { return TP_ACK; }
    }

    public record MapManualConnectPayload(BlockPos from, BlockPos to) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapManualConnectPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MapManualConnectPayload::from,
            BlockPos.STREAM_CODEC, MapManualConnectPayload::to,
            MapManualConnectPayload::new
        );
        @Override public Type<MapManualConnectPayload> type() { return MAN_REQ; }
    }

    public record MapAccessSyncPayload(boolean allowed) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapAccessSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MapAccessSyncPayload::allowed,
            MapAccessSyncPayload::new
        );
        @Override public Type<MapAccessSyncPayload> type() { return ACCESS_SYNC; }
    }

    public record MapSearchRequestPayload(int requestSeq,
                                          ResourceLocation dimension,
                                          String query) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapSearchRequestPayload> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.requestSeq);
                    buf.writeResourceLocation(value.dimension);
                    buf.writeUtf(value.query, MapStructureSearchService.MAX_QUERY_LENGTH);
                },
                buf -> new MapSearchRequestPayload(
                        buf.readVarInt(),
                        buf.readResourceLocation(),
                        buf.readUtf(MapStructureSearchService.MAX_QUERY_LENGTH))
        );

        @Override public Type<MapSearchRequestPayload> type() { return SEARCH_REQ; }
    }

    public record MapSearchResponsePayload(int requestSeq,
                                           ResourceLocation dimension,
                                           boolean success,
                                           List<MapSearchResult> results) implements CustomPacketPayload {
        public MapSearchResponsePayload {
            results = results == null ? List.of() : List.copyOf(results);
        }

        public static final StreamCodec<FriendlyByteBuf, MapSearchResponsePayload> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.requestSeq);
                    buf.writeResourceLocation(value.dimension);
                    buf.writeBoolean(value.success);
                    int count = Math.min(value.results.size(), MapStructureSearchService.MAX_RESULTS);
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        MapSearchResult result = value.results.get(i);
                        buf.writeBlockPos(result.pos());
                        buf.writeUtf(result.structureId(), 256);
                        buf.writeVarInt(result.source() + 1);
                    }
                },
                buf -> {
                    int requestSeq = buf.readVarInt();
                    ResourceLocation dimension = buf.readResourceLocation();
                    boolean success = buf.readBoolean();
                    int count = buf.readVarInt();
                    if (count < 0 || count > MapStructureSearchService.MAX_RESULTS) {
                        throw new DecoderException("search result count out of range: " + count);
                    }
                    ArrayList<MapSearchResult> results = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        results.add(new MapSearchResult(
                                buf.readBlockPos(),
                                buf.readUtf(256),
                                buf.readVarInt() - 1));
                    }
                    return new MapSearchResponsePayload(requestSeq, dimension, success, results);
                }
        );

        @Override public Type<MapSearchResponsePayload> type() { return SEARCH_RESP; }
    }

    private static void writeAutomaticPlanningSamplingBounds(
            FriendlyByteBuf buf,
            List<AutomaticPlanningSamplingBounds> bounds) {
        List<AutomaticPlanningSamplingBounds> safeBounds = immutableAutomaticPlanningSamplingBounds(bounds);
        buf.writeVarInt(safeBounds.size());
        for (AutomaticPlanningSamplingBounds bound : safeBounds) {
            buf.writeInt(bound.minX());
            buf.writeInt(bound.minZ());
            buf.writeInt(bound.maxX());
            buf.writeInt(bound.maxZ());
        }
    }

    private static List<AutomaticPlanningSamplingBounds> readAutomaticPlanningSamplingBounds(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_AUTOMATIC_PLANNING_SAMPLING_REGIONS) {
            throw new DecoderException("automatic planning sampling region count out of range: " + count);
        }
        ArrayList<AutomaticPlanningSamplingBounds> bounds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            bounds.add(new AutomaticPlanningSamplingBounds(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()));
        }
        return List.copyOf(bounds);
    }

    private static List<AutomaticPlanningSamplingBounds> immutableAutomaticPlanningSamplingBounds(
            List<AutomaticPlanningSamplingBounds> bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return List.of();
        }
        ArrayList<AutomaticPlanningSamplingBounds> copy = new ArrayList<>(bounds.size());
        for (AutomaticPlanningSamplingBounds bound : bounds) {
            if (bound != null) {
                copy.add(bound);
            }
        }
        if (copy.size() > MAX_AUTOMATIC_PLANNING_SAMPLING_REGIONS) {
            return List.copyOf(copy.subList(0, MAX_AUTOMATIC_PLANNING_SAMPLING_REGIONS));
        }
        return List.copyOf(copy);
    }
}

/* 文件职责：定义道路地图客户端与服务端之间的网络载荷。 */
package net.shiroha233.roadweaver.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
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
import java.util.function.BiConsumer;
import java.util.function.Function;

public class MapNetworkPayloads {
    public interface Codec<T> {
        void encode(FriendlyByteBuf buf, T value);
        T decode(FriendlyByteBuf buf);

        static <T> Codec<T> of(BiConsumer<FriendlyByteBuf, T> encoder,
                               Function<FriendlyByteBuf, T> decoder) {
            return new Codec<>() {
                @Override
                public void encode(FriendlyByteBuf buf, T value) {
                    encoder.accept(buf, value);
                }

                @Override
                public T decode(FriendlyByteBuf buf) {
                    return decoder.apply(buf);
                }
            };
        }
    }

    private static final int MAX_AUTOMATIC_PLANNING_SAMPLING_REGIONS = 256;

    public static final ResourceLocation REQ_RECT = new ResourceLocation("roadweaver", "map_request_rect");
    public static final ResourceLocation SNAP = new ResourceLocation("roadweaver", "map_snapshot");
    public static final ResourceLocation PATCH = new ResourceLocation("roadweaver", "map_patch");
    public static final ResourceLocation AUTO_PLANNING_SAMPLING = new ResourceLocation("roadweaver", "map_automatic_planning_sampling");
    public static final ResourceLocation TP_REQ = new ResourceLocation("roadweaver", "map_teleport");
    public static final ResourceLocation TP_ACK = new ResourceLocation("roadweaver", "map_teleport_ack");
    public static final ResourceLocation MAN_REQ = new ResourceLocation("roadweaver", "map_manual_connect");
    public static final ResourceLocation ACCESS_SYNC = new ResourceLocation("roadweaver", "map_access_sync");
    public static final ResourceLocation SEARCH_REQ = new ResourceLocation("roadweaver", "map_search_request");
    public static final ResourceLocation SEARCH_RESP = new ResourceLocation("roadweaver", "map_search_response");

    public record MapRequestRectPayload(int requestSeq,
                                        ResourceLocation dimension,
                                        MapLoadPhase phase,
                                        int responseIndex,
                                        int minX,
                                        int minZ,
                                        int maxX,
                                        int maxZ) {
        public static final Codec<MapRequestRectPayload> CODEC = Codec.of(
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
    }

    public record MapSnapshotPayload(int requestSeq,
                                     ResourceLocation dimension,
                                     MapLoadPhase phase,
                                     int responseIndex,
                                     MapSnapshot snapshot,
                                     List<AutomaticPlanningSamplingBounds> automaticPlanningSamplingBounds) {
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

        public static final Codec<MapSnapshotPayload> CODEC = Codec.of(
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
    }

    public record MapPatchPayload(ResourceLocation dimension, MapSnapshotPatch patch) {
        public static final Codec<MapPatchPayload> CODEC = Codec.of(
            (buf, val) -> {
                buf.writeResourceLocation(val.dimension);
                MapSnapshotCodec.writePatch(buf, val.patch);
            },
            buf -> new MapPatchPayload(buf.readResourceLocation(), MapSnapshotCodec.readPatch(buf))
        );
    }

    public record MapAutomaticPlanningSamplingPayload(
            ResourceLocation dimension,
            List<AutomaticPlanningSamplingBounds> bounds) {
        public MapAutomaticPlanningSamplingPayload {
            bounds = immutableAutomaticPlanningSamplingBounds(bounds);
        }

        public static final Codec<MapAutomaticPlanningSamplingPayload> CODEC = Codec.of(
                (buf, value) -> {
                    buf.writeResourceLocation(value.dimension);
                    writeAutomaticPlanningSamplingBounds(buf, value.bounds);
                },
                buf -> new MapAutomaticPlanningSamplingPayload(
                        buf.readResourceLocation(),
                        readAutomaticPlanningSamplingBounds(buf))
        );

    }

    public record MapTeleportPayload(int x, int y, int z) {
        public static final Codec<MapTeleportPayload> CODEC = Codec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.x);
                    buf.writeVarInt(value.y);
                    buf.writeVarInt(value.z);
                },
                buf -> new MapTeleportPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    }

    public record MapTeleportAckPayload(boolean success, int x, int y, int z) {
        public static final Codec<MapTeleportAckPayload> CODEC = Codec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.success);
                    buf.writeVarInt(value.x);
                    buf.writeVarInt(value.y);
                    buf.writeVarInt(value.z);
                },
                buf -> new MapTeleportAckPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    }

    public record MapManualConnectPayload(BlockPos from, BlockPos to) {
        public static final Codec<MapManualConnectPayload> CODEC = Codec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.from);
                    buf.writeBlockPos(value.to);
                },
                buf -> new MapManualConnectPayload(buf.readBlockPos(), buf.readBlockPos()));
    }

    public record MapAccessSyncPayload(boolean allowed) {
        public static final Codec<MapAccessSyncPayload> CODEC = Codec.of(
                (buf, value) -> buf.writeBoolean(value.allowed),
                buf -> new MapAccessSyncPayload(buf.readBoolean()));
    }

    public record MapSearchRequestPayload(int requestSeq,
                                          ResourceLocation dimension,
                                          String query) {
        public static final Codec<MapSearchRequestPayload> CODEC = Codec.of(
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

    }

    public record MapSearchResponsePayload(int requestSeq,
                                           ResourceLocation dimension,
                                           boolean success,
                                           List<MapSearchResult> results) {
        public MapSearchResponsePayload {
            results = results == null ? List.of() : List.copyOf(results);
        }

        public static final Codec<MapSearchResponsePayload> CODEC = Codec.of(
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

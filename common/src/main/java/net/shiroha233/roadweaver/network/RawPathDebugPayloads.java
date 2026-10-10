package net.shiroha233.roadweaver.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.planning.path.DebugRawPath;
import net.shiroha233.roadweaver.planning.path.RawPathDebugService;

import java.util.ArrayList;
import java.util.List;

/** Bounded world-space debug requests and responses; coordinates are always chosen by the server. */
public final class RawPathDebugPayloads {
    public static final ResourceLocation REQUEST = new ResourceLocation("roadweaver", "raw_path_debug_request");
    public static final ResourceLocation RESPONSE = new ResourceLocation("roadweaver", "raw_path_debug_response");

    private RawPathDebugPayloads() {}

    public record Request(int sequence, ResourceLocation dimension) {
        public static final MapNetworkPayloads.Codec<Request> CODEC = MapNetworkPayloads.Codec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.sequence());
                    buf.writeResourceLocation(value.dimension());
                },
                buf -> new Request(buf.readVarInt(), buf.readResourceLocation()));

    }

    public record Response(int sequence, ResourceLocation dimension, boolean captureEnabled,
                           List<DebugRawPath> paths) {
        public Response {
            paths = List.copyOf(paths == null ? List.of() : paths);
        }

        public static final MapNetworkPayloads.Codec<Response> CODEC = MapNetworkPayloads.Codec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.sequence());
                    buf.writeResourceLocation(value.dimension());
                    buf.writeBoolean(value.captureEnabled());
                    buf.writeVarInt(value.paths().size());
                    for (DebugRawPath path : value.paths()) {
                        buf.writeEnum(path.mode());
                        writeNodes(buf, path.coarse());
                        writeNodes(buf, path.accurate());
                    }
                },
                buf -> {
                    int sequence = buf.readVarInt();
                    ResourceLocation dimension = buf.readResourceLocation();
                    boolean captureEnabled = buf.readBoolean();
                    int count = buf.readVarInt();
                    if (count < 0 || count > RawPathDebugService.MAX_RESPONSE_POINTS / 2) {
                        throw new DecoderException("invalid debug path count: " + count);
                    }
                    int[] budget = {RawPathDebugService.MAX_RESPONSE_POINTS};
                    ArrayList<DebugRawPath> paths = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        TerrainSamplingMode mode = buf.readEnum(TerrainSamplingMode.class);
                        List<BlockPos> coarse = readNodes(buf, budget);
                        List<BlockPos> accurate = readNodes(buf, budget);
                        paths.add(new DebugRawPath(mode, coarse, accurate));
                    }
                    return new Response(sequence, dimension, captureEnabled, paths);
                });

    }

    private static void writeNodes(FriendlyByteBuf buf, List<BlockPos> nodes) {
        buf.writeVarInt(nodes.size());
        for (BlockPos node : nodes) buf.writeBlockPos(node);
    }

    private static List<BlockPos> readNodes(FriendlyByteBuf buf, int[] budget) {
        int count = buf.readVarInt();
        if (count < 0 || count > budget[0]) throw new DecoderException("invalid debug path node count: " + count);
        budget[0] -= count;
        ArrayList<BlockPos> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) nodes.add(buf.readBlockPos());
        return List.copyOf(nodes);
    }
}

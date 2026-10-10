package net.shiroha233.roadweaver.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.planning.path.DebugRawPath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RawPathDebugPayloadsTest {
    @Test
    void roundTripsSamplingModesAndThreeDimensionalNodes() {
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        RawPathDebugPayloads.Response response = new RawPathDebugPayloads.Response(42, dimension, true, List.of(
                new DebugRawPath(TerrainSamplingMode.COARSE_CORRIDOR,
                        List.of(new BlockPos(-16, 60, 40), new BlockPos(-8, 61, 40)),
                        List.of(new BlockPos(-16, 68, 40), new BlockPos(-8, 69, 40))),
                new DebugRawPath(TerrainSamplingMode.FULL_REGION, List.of(),
                        List.of(new BlockPos(100, 75, 100), new BlockPos(108, 76, 100)))));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RawPathDebugPayloads.Response.CODEC.encode(buffer, response);
            assertEquals(response, RawPathDebugPayloads.Response.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}

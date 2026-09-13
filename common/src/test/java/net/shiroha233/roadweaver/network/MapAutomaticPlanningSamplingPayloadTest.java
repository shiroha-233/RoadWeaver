/* 文件职责：验证自动规划采样范围网络载荷的编码与解码。 */
package net.shiroha233.roadweaver.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapAutomaticPlanningSamplingPayloadTest {

    @Test
    void roundTripsAllActiveRanges() {
        MapNetworkPayloads.MapAutomaticPlanningSamplingPayload payload =
                new MapNetworkPayloads.MapAutomaticPlanningSamplingPayload(
                        new ResourceLocation("minecraft", "overworld"),
                        List.of(
                                new AutomaticPlanningSamplingBounds(-256, -128, 0, 128),
                                new AutomaticPlanningSamplingBounds(512, 256, 768, 512)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.CODEC.encode(buffer, payload);

            assertEquals(payload, MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}

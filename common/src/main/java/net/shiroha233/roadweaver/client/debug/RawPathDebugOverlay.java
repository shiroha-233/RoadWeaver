package net.shiroha233.roadweaver.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.shiroha233.roadweaver.network.ClientNetBridge;
import net.shiroha233.roadweaver.network.RawPathDebugPayloads;
import net.shiroha233.roadweaver.planning.path.DebugRawPath;

import java.util.List;

/** Client-only 3D overlay of the actual pre-postprocessing search nodes. */
public final class RawPathDebugOverlay {
    private static final int REFRESH_TICKS = 40;
    private static final int COARSE_COLOR = 0xFFAA27;
    private static final int ACCURATE_COLOR = 0x29DEFF;
    private static boolean visible;
    private static int nextSequence;
    private static int expectedSequence;
    private static int ticksUntilRefresh;
    private static boolean warnedCaptureDisabled;
    private static ResourceLocation dimension;
    private static List<DebugRawPath> paths = List.of();

    private RawPathDebugOverlay() {}

    public static void toggle(Minecraft client) {
        visible = !visible;
        paths = List.of();
        ticksUntilRefresh = 0;
        if (client.player != null) {
            client.player.displayClientMessage(Component.translatable(
                    visible ? "debug.roadweaver.raw_paths.on" : "debug.roadweaver.raw_paths.off"), true);
        }
    }

    public static void reset() {
        visible = false;
        paths = List.of();
        dimension = null;
        ticksUntilRefresh = 0;
        warnedCaptureDisabled = false;
        expectedSequence = ++nextSequence;
    }

    public static void tick(Minecraft client) {
        if (!visible || client.player == null || client.level == null) return;
        ResourceLocation current = client.level.dimension().location();
        if (!current.equals(dimension)) {
            dimension = current;
            paths = List.of();
            ticksUntilRefresh = 0;
            warnedCaptureDisabled = false;
        }
        if (--ticksUntilRefresh > 0) return;
        ticksUntilRefresh = REFRESH_TICKS * 3; // Retry an unanswered request without rejecting a slow valid reply.
        expectedSequence = ++nextSequence;
        ClientNetBridge.requestRawPathDebug(expectedSequence, dimension);
    }

    public static void accept(RawPathDebugPayloads.Response response) {
        Minecraft client = Minecraft.getInstance();
        if (!visible || client.level == null || !response.dimension().equals(client.level.dimension().location())
                || response.sequence() != expectedSequence) return;
        ticksUntilRefresh = REFRESH_TICKS;
        paths = response.paths();
        if (!response.captureEnabled() && !warnedCaptureDisabled && client.player != null) {
            warnedCaptureDisabled = true;
            client.player.displayClientMessage(Component.translatable("debug.roadweaver.raw_paths.capture_disabled"), false);
        }
    }

    public static void render(PoseStack pose, Camera camera) {
        Minecraft client = Minecraft.getInstance();
        if (!visible || client.level == null || pose == null || paths.isEmpty()) return;
        if (!client.level.dimension().location().equals(dimension)) return;
        Vec3 eye = camera.getPosition();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RenderType.lines());
        for (DebugRawPath path : paths) {
            drawNodes(pose, vertices, eye, path.coarse(), COARSE_COLOR, -0.18f);
            drawNodes(pose, vertices, eye, path.accurate(), ACCURATE_COLOR, 0.18f);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void drawNodes(PoseStack pose, VertexConsumer vertices, Vec3 camera,
                                  List<BlockPos> nodes, int color, float offset) {
        for (int i = 0; i < nodes.size(); i++) {
            BlockPos node = nodes.get(i);
            double x = node.getX() + 0.5 + offset - camera.x;
            double y = node.getY() + 1.25 - camera.y;
            double z = node.getZ() + 0.5 + offset - camera.z;
            // A small cross makes each unsmoothed search node visible independently of the segments.
            line(pose, vertices, x - 0.18, y, z, x + 0.18, y, z, color);
            line(pose, vertices, x, y, z - 0.18, x, y, z + 0.18, color);
            if (i + 1 < nodes.size()) {
                BlockPos next = nodes.get(i + 1);
                line(pose, vertices, x, y, z,
                        next.getX() + 0.5 + offset - camera.x,
                        next.getY() + 1.25 - camera.y,
                        next.getZ() + 0.5 + offset - camera.z, color);
            }
        }
    }

    private static void line(PoseStack pose, VertexConsumer vertices,
                             double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.0001f) return;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        vertices.addVertex(pose.last().pose(), (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, 255).setNormal(pose.last(), dx / length, dy / length, dz / length);
        vertices.addVertex(pose.last().pose(), (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, 255).setNormal(pose.last(), dx / length, dy / length, dz / length);
    }
}

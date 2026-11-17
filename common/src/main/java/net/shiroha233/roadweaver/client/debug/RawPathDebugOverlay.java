package net.shiroha233.roadweaver.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.shiroha233.roadweaver.network.payload.RawPathDebugPayloads;
import net.shiroha233.roadweaver.network.platform.ClientNetBridge;
import net.shiroha233.roadweaver.planning.path.DebugRawPath;

import java.util.List;

/** Client-only 3D overlay of the actual pre-postprocessing search nodes. */
public final class RawPathDebugOverlay {
    private static final int REFRESH_TICKS = 40;
    private static final int COARSE_COLOR = 0xFFFFAA27;
    private static final int ACCURATE_COLOR = 0xFF29DEFF;
    private static final float NODE_MARKER_SIZE = 2.0F;
    private static final double NODE_OFFSET = 0.18D;
    private static boolean visible;
    private static int nextSequence;
    private static int expectedSequence;
    private static int ticksUntilRefresh;
    private static boolean warnedCaptureDisabled;
    private static Identifier dimension;
    private static List<DebugRawPath> paths = List.of();

    private RawPathDebugOverlay() {}

    public static void toggle(Minecraft client) {
        visible = !visible;
        paths = List.of();
        ticksUntilRefresh = 0;
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(
                    visible ? "debug.roadweaver.raw_paths.on" : "debug.roadweaver.raw_paths.off"));
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
        Identifier current = client.level.dimension().identifier();
        if (!current.equals(dimension)) {
            dimension = current;
            paths = List.of();
            ticksUntilRefresh = 0;
            warnedCaptureDisabled = false;
        }
        if (--ticksUntilRefresh > 0) return;
        ticksUntilRefresh = REFRESH_TICKS * 3;
        expectedSequence = ++nextSequence;
        ClientNetBridge.requestRawPathDebug(expectedSequence, dimension);
    }

    public static void accept(RawPathDebugPayloads.Response response) {
        Minecraft client = Minecraft.getInstance();
        if (!visible || client.level == null || !response.dimension().equals(client.level.dimension().identifier())
                || response.sequence() != expectedSequence) return;
        ticksUntilRefresh = REFRESH_TICKS;
        paths = response.paths();
        if (!response.captureEnabled() && !warnedCaptureDisabled && client.player != null) {
            warnedCaptureDisabled = true;
            client.player.sendSystemMessage(Component.translatable("debug.roadweaver.raw_paths.capture_disabled"));
        }
    }

    /**
     * 把当前路径节点提交给 26.3 的 gizmo 收集器。
     *
     * <p>26.3 移除了 MultiBufferSource/VertexConsumer 的即时绘制入口，世界内线段改由 gizmo 管线统一收集，
     * 因此本方法必须在原版 gizmo 收集阶段内调用（平台侧渲染钩子）。
     */
    public static void emitGizmos() {
        Minecraft client = Minecraft.getInstance();
        if (!visible || client.level == null || paths.isEmpty()) return;
        if (!client.level.dimension().identifier().equals(dimension)) return;
        for (DebugRawPath path : paths) {
            drawNodes(path.coarse(), COARSE_COLOR, -NODE_OFFSET);
            drawNodes(path.accurate(), ACCURATE_COLOR, NODE_OFFSET);
        }
    }

    private static void drawNodes(List<BlockPos> nodes, int color, double offset) {
        for (int i = 0; i < nodes.size(); i++) {
            Vec3 center = nodeCenter(nodes.get(i), offset);
            Gizmos.line(center.add(-NODE_OFFSET, 0.0D, 0.0D), center.add(NODE_OFFSET, 0.0D, 0.0D), color);
            Gizmos.line(center.add(0.0D, 0.0D, -NODE_OFFSET), center.add(0.0D, 0.0D, NODE_OFFSET), color);
            if (i + 1 < nodes.size()) {
                Gizmos.line(center, nodeCenter(nodes.get(i + 1), offset), color);
            }
        }
        if (!nodes.isEmpty()) {
            Gizmos.point(nodeCenter(nodes.get(0), offset), color, NODE_MARKER_SIZE);
        }
    }

    private static Vec3 nodeCenter(BlockPos node, double offset) {
        return new Vec3(node.getX() + 0.5D + offset, node.getY() + 1.25D, node.getZ() + 0.5D + offset);
    }
}

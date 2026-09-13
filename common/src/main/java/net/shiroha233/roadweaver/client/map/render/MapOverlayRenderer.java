package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.client.map.MapInputHandler;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;

/**
 * 地图前景叠加层绘制。
 */
public final class MapOverlayRenderer {
    private MapOverlayRenderer() {}

    public static void renderManualModePreview(GuiGraphics g,
                                               MapSnapshot snapshot,
                                               MapView view,
                                               MapInputHandler inputHandler,
                                               BlockPos selectedA,
                                               int mouseX,
                                               int mouseY,
                                               int contentW,
                                               int contentH,
                                               int left,
                                               int top,
                                               int right,
                                               int bottom,
                                               int selectedPointSize,
                                               int lineThickness) {
        if (selectedA == null) return;
        if (!view.isInViewWorld(selectedA.getX(), selectedA.getZ())) return;

        int sxA = view.toScreenX(selectedA.getX(), 0, 0, contentW);
        int syA = view.toScreenY(selectedA.getZ(), 0, 0, contentH);
        StructureIconRenderer.renderOutline(g, sxA, syA, selectedPointSize,
                MapTheme.COLOR_SELECTED, left, top, right, bottom);

        if (!inputHandler.insideMap(mouseX, mouseY)) return;
        int sxB;
        int syB;
        BlockPos hover = inputHandler.findNearestStructure(snapshot, mouseX, mouseY);
        if (hover != null && view.isInViewWorld(hover.getX(), hover.getZ())) {
            sxB = view.toScreenX(hover.getX(), 0, 0, contentW);
            syB = view.toScreenY(hover.getZ(), 0, 0, contentH);
        } else {
            sxB = mouseX;
            syB = mouseY;
        }
        RenderUtils.drawThickDashedLine(g, sxA, syA, sxB, syB,
                MapTheme.COLOR_PREVIEW_LINE, lineThickness,
                MapTheme.DASH_LENGTH, MapTheme.DASH_GAP,
                left, top, right, bottom);
    }

    public static void renderPlayer(GuiGraphics g,
                                    Minecraft mc,
                                    MapInputHandler inputHandler,
                                    MapView view,
                                    int contentW,
                                    int contentH,
                                    int left,
                                    int top,
                                    int right,
                                    int bottom) {
        if (mc == null || mc.player == null) return;

        double wx = mc.player.getX();
        double wz = mc.player.getZ();
        int sx = view.toScreenX((int) Math.round(wx), 0, 0, contentW);
        int sy = view.toScreenY((int) Math.round(wz), 0, 0, contentH);
        if (!inputHandler.insideMap(sx, sy)) return;

        ResourceLocation skinTexture = mc.player.getSkinTextureLocation();
        float yaw = mc.player.getYRot();
        MapRenderers.drawPlayerAvatar(g, skinTexture, sx, sy, yaw,
                MapTheme.PLAYER_AVATAR_SIZE,
                MapTheme.COLOR_PLAYER_AVATAR_FRAME,
                MapTheme.COLOR_PLAYER_DIRECTION,
                MapTheme.COLOR_PLAYER_DIRECTION_OUTLINE,
                MapTheme.PLAYER_DIRECTION_TIP_LEN,
                MapTheme.PLAYER_DIRECTION_BASE_OFFSET,
                MapTheme.PLAYER_DIRECTION_HALF_WIDTH,
                left, top, right, bottom);
    }
}

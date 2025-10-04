package net.countered.settlementroads.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * 遵循Forge规范的道路网络调试屏幕
 * 功能: 显示结构节点、道路连接、支持平移/缩放、点击传送
 */
public class RoadDebugScreen extends Screen {

    private static final int RADIUS = 5;
    private static final int PADDING = 20;

    private final List<BlockPos> structures;
    private final List<Records.StructureConnection> connections;
    private final List<Records.RoadData> roads;

    private final Map<BlockPos, ScreenPos> screenPositions = new HashMap<>();
    private float offsetx, offsety;
    private double scale = 1.0;
    private int minZ, maxZ, minX, maxX;
    private boolean boundsDirty = true;
    private BlockPos hoveredStructure = null;

    public RoadDebugScreen(List<BlockPos> structures,
                           List<Records.StructureConnection> connections,
                           List<Records.RoadData> roads) {
        super(Component.translatable("gui.roadweaver.debug_map.title"));
        // 创建不可变副本，避免并发修改异常
        this.structures = structures != null ? new ArrayList<>(structures) : new ArrayList<>();
        this.connections = connections != null ? new ArrayList<>(connections) : new ArrayList<>();
        this.roads = roads != null ? new ArrayList<>(roads) : new ArrayList<>();

        if (!this.structures.isEmpty()) {
            minX = this.structures.stream().mapToInt(BlockPos::getX).min().orElse(0);
            maxX = this.structures.stream().mapToInt(BlockPos::getX).max().orElse(0);
            minZ = this.structures.stream().mapToInt(BlockPos::getZ).min().orElse(0);
            maxZ = this.structures.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        }
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        calculateBounds();
        updateOffsetAndScale();

        renderConnections(ctx);
        renderRoads(ctx);
        renderStructures(ctx);
        renderPlayerMarker(ctx);

        drawTitle(ctx);
        drawScalePanel(ctx);
        drawLegendPanel(ctx);

        if (hoveredStructure != null) {
            drawTooltip(ctx, hoveredStructure, mouseX, mouseY);
        }

        updateHoveredStructure(mouseX, mouseY);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) { // 左键拖拽
            offsetx = (float) (offsetx + dragX * scale);
            offsety = (float) (offsety + dragY * scale);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 确保管理缩放比例在合理范围内
        double previousScale = scale;
        scale = Math.max(0.01, Math.min(100.0, scale * Math.pow(1.1, delta)));

        // 以鼠标位置为中心进行缩放
        offsetx += (float) ((mouseX - offsetx) * (scale / previousScale - 1)) * 0.5f;
        offsety += (float) ((mouseY - offsety) * (scale / previousScale - 1)) * 0.5f;

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hoveredStructure != null) {
            teleportToStructure(hoveredStructure);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void calculateBounds() {
        if (boundsDirty && !structures.isEmpty()) {
            minX = structures.stream().mapToInt(BlockPos::getX).min().orElse(0);
            maxX = structures.stream().mapToInt(BlockPos::getX).max().orElse(0);
            minZ = structures.stream().mapToInt(BlockPos::getZ).min().orElse(0);
            maxZ = structures.stream().mapToInt(BlockPos::getZ).max().orElse(0);
            boundsDirty = false;
        }
    }

    private void updateOffsetAndScale() {
        if (!structures.isEmpty()) {
            int width = this.width - PADDING * 3;
            int height = this.height - 150; // 为UI组件留出空间

            double scaleX = (double) width / (maxX - minX);
            double scaleY = (double) height / (maxZ - minZ);
            scale = Math.min(scaleX, scaleY) * 0.8; // 安全边界

            // 设置偏移量，使地图居中
            double worldCenterX = minX + (maxX - minX) / 2.0;
            double worldCenterZ = minZ + (maxZ - minZ) / 2.0;
            offsetx = (float) (this.width / 2.0 - worldCenterX * scale);
            offsety = (float) (this.height / 2.0 - worldCenterZ * scale);
        }
    }

    private void renderStructures(GuiGraphics ctx) {
        for (BlockPos structurePos : structures) {
            ScreenPos screenPos = worldToScreen(structurePos);
            int x = (int) screenPos.x;
            int y = (int) screenPos.y;
            boolean isHovered = hoveredStructure != null && hoveredStructure.equals(structurePos);

            if (x > -10 && x < this.width + 10 && y > -10 && y < this.height + 10) {
                // 绘制结构节点
                drawCircleOutline(ctx, x, y, isHovered ? RADIUS + 2 : RADIUS, 0xFF27AE60);

                // 添加结构标识文本
                if (isHovered) {
                    ctx.drawString(this.font, Component.literal("Structure"), x + 10, y - 10, 0xFFFFFFFF);
                }
            }

            screenPositions.put(structurePos, screenPos);
        }
    }

    private void renderConnections(GuiGraphics ctx) {
        for (Records.StructureConnection connection : connections) {
            ScreenPos fromScreen = worldToScreen(connection.from());
            ScreenPos toScreen = worldToScreen(connection.to());

            String statusName = connection.status().name().toLowerCase();
            if (statusName.equals("completed")) continue; // 已完成连接不显示

            // 确定颜色值
            int color = 0xFFFFFFFF;
            if (statusName.equals("planned")) color = 0xFFFFF176;        // 黄色 - 计划中
            else if (statusName.equals("generating")) color = 0xFFF4511E; // 橙色 - 生成中
            else if (statusName.equals("failed")) color = 0xFFD84315;     // 红色 - 失败

            if (color != 0xFFFFFFFF) {
                drawLine(ctx, (int) fromScreen.x, (int) fromScreen.y, (int) toScreen.x, (int) toScreen.y, color);
            }
        }
    }

    private void renderRoads(GuiGraphics ctx) {
        for (Records.RoadData road : roads) {
            for (Records.RoadSegmentPlacement segment : road.roadSegmentList()) {
                for (BlockPos pos : segment.positions()) {
                    ScreenPos screenPos = worldToScreen(pos);
                    ctx.fill((int) screenPos.x - 1, (int) screenPos.y - 1, (int) screenPos.x + 1, (int) screenPos.y + 1, 0xFF8C786C);
                }
            }
        }
    }

    private void renderPlayerMarker(GuiGraphics ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        BlockPos playerPos = new BlockPos((int)client.player.getX(), (int)client.player.getY(), (int)client.player.getZ());
        ScreenPos playerScreenPos = worldToScreen(playerPos);

        // 用白色金字塔形状表示玩家位置
        drawCircle(ctx, (int) playerScreenPos.x, (int) playerScreenPos.y, 8, 0xFFFFFFFF);
    }

    private ScreenPos worldToScreen(BlockPos worldPos) {
        double worldX = worldPos.getX();
        double worldZ = worldPos.getZ();
        return new ScreenPos(
                (float) (offsetx + worldX * scale),
                (float) (offsety + worldZ * scale)
        );
    }

    private BlockPos screenToWorld(float screenX, float screenY) {
        double worldX = (screenX - offsetx) / scale;
        double worldZ = (screenY - offsety) / scale;
        return new BlockPos((int)worldX, 0, (int)worldZ);
    }

    private void updateHoveredStructure(int mouseX, int mouseY) {
        double worldX = (mouseX - offsetx) / scale;
        double worldZ = (mouseY - offsety) / scale;

        BlockPos closest = null;
        double minDistance = Double.MAX_VALUE;

        for (BlockPos structure : structures) {
            double dx = structure.getX() - worldX;
            double dz = structure.getZ() - worldZ;
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance < RADIUS * scale && distance < minDistance) {
                minDistance = distance;
                closest = structure;
            }
        }

        hoveredStructure = closest;
    }

    private void teleportToStructure(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (client.getCurrentServer() != null) {
            // 使用Minecraft CP工具检测服务器
            ServerPlayer serverPlayer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(client.player.getUUID());
            if (serverPlayer != null) {
                // 使用服务器线程执行传送
                serverPlayer.teleportTo(serverPlayer.serverLevel(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
            }
        }
    }

    private void drawTitle(GuiGraphics ctx) {
        ctx.drawString(this.font, Component.translatable("gui.roadweaver.debug_map.title"), 10, 10, 0xFFFFFFFF);
    }

    private void drawScalePanel(GuiGraphics ctx) {
        int x = this.width - 110;
        int y = 10;
        int barLength = 100;

        // 绘制比例尺
        ctx.fill(x, y, x + barLength / 2, y + 5, 0xFFFFFFFF);
        ctx.fill(x + barLength / 2, y, x + barLength, y + 5, 0xFF888888);

        // 100像素对应1000格世界坐标
        double actualDistance = barLength / scale;
        ctx.drawString(this.font, Component.literal((int) actualDistance + "m"), x, y + 6, 0xFFFFFFFF);
    }

    private void drawLegendPanel(GuiGraphics ctx) {
        int x = this.width - 110;
        int y = 50;
        int lineHeight = 12;

        // 额色说明
        ctx.drawString(this.font, Component.translatable("Status Legend:"), x, y, 0xFFFFFFFF);
        y += lineHeight;

        drawLegendItem(ctx, x, y, 0xFFFFF176, "Planned");
        y += lineHeight;
        drawLegendItem(ctx, x, y, 0xFFF4511E, "Generating");
        y += lineHeight;
        drawLegendItem(ctx, x, y, 0xFFD84315, "Failed");
        y += lineHeight;
        drawLegendItem(ctx, x, y, 0xFF27AE60, "Structure");
    }

    private void drawLegendItem(GuiGraphics ctx, int x, int y, int color, String text) {
        ctx.fill(x, y, x + 10, y + 10, color);
        ctx.drawString(this.font, Component.literal(text), x + 15, y + 2, 0xFFFFFFFF);
    }

    private void drawTooltip(GuiGraphics ctx, BlockPos pos, int mouseX, int mouseY) {
        String tooltip = String.format("Structure at: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
        ctx.renderTooltip(this.font, Component.literal(tooltip), mouseX, mouseY);
    }

    private static void drawLine(GuiGraphics ctx, int x0, int y0, int x1, int y1, int color) {
        // 使用 GameRenderer.DrawLine 来绘制连接线
        ctx.pose().pushPose();
        // 替换简单线条绘制，使用填充矩形模拟线条
        if (Math.abs(x1 - x0) > Math.abs(y1 - y0)) {
            // 鎟向line
            int left = Math.min(x0, x1);
            int right = Math.max(x0, x1);
            ctx.fill(left, y0, right + 1, y0 + 1, color);
        } else {
            // 縱line
            int top = Math.min(y0, y1);
            int bottom = Math.max(y0, y1);
            ctx.fill(x0, top, x0 + 1, bottom + 1, color);
        }
        ctx.pose().popPose();
    }

    private static void drawCircle(GuiGraphics ctx, int cx, int cy, int r, int color) {
        // 椭圆填充
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                if (x * x + y * y <= r * r) {
                    ctx.fill(cx + x - 1, cy + y - 1, cx + x + 1, cy + y + 1, color);
                }
            }
        }
    }

    private static void drawCircleOutline(GuiGraphics ctx, int cx, int cy, int r, int color) {
        // 使用中点圆算法绘制圆圈轮廓
        int x = 0, y = r;
        int d = 3 - 2 * r;

        while (x <= y) {
            ctx.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
            ctx.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
            ctx.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
            ctx.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
            ctx.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
            ctx.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
            ctx.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
            ctx.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);

            if (d < 0) {
                d = d + 4 * x + 6;
            } else {
                d = d + 4 * (x - y) + 10;
                y--;
            }
            x++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 设置为false以确保在单人游戏中不会暂停游戏
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private record ScreenPos(float x, float y) {}
}

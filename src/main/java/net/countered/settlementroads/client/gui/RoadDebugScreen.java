package net.countered.settlementroads.client.gui;

import net.countered.settlementroads.helpers.Records;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * 道路网络调试屏幕
 * 功能: 显示结构节点、道路连接、支持平移/缩放、点击传送
 */
public class RoadDebugScreen extends Screen {

    private static final int RADIUS = 5;
    private static final int PADDING = 20;
    private static final int TARGET_GRID_PX = 80;

    private final List<BlockPos> structures;
    private final List<Records.StructureConnection> connections;
    private final List<Records.RoadData> roads;

    private final Map<BlockPos, ScreenPos> screenPositions = new HashMap<>();
    private final Map<String, Integer> statusColors = Map.of(
            "structure", 0xFF27AE60,   // 绿色 - 结构
            "planned", 0xFFF2C94C,     // 黄色 - 计划中
            "generating", 0xFFE67E22,  // 橙色 - 生成中
            "completed", 0xFF27AE60,   // 绿色 - 已完成（不显示）
            "failed", 0xFFE74C3C,      // 红色 - 生成失败
            "road", 0xFF3498DB         // 蓝色 - 道路
    );

    private boolean dragging = false;
    private boolean firstLayout = true;
    private double zoom = 1.0;
    private double offsetX = 0;
    private double offsetY = 0;
    private double baseScale = 1.0;
    private int minX, maxX, minZ, maxZ;

    public RoadDebugScreen(List<BlockPos> structures, 
                          List<Records.StructureConnection> connections,
                          List<Records.RoadData> roads) {
        super(Text.translatable("gui.roadweaver.debug_map.title"));
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
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        computeLayout();

        // 绘制主背景面板 - 深色半透明
        drawPanel(ctx, PADDING, PADDING, width - PADDING, height - PADDING, 0xE0101010, 0xFF2C2C2C);

        // 绘制网格
        drawGrid(ctx);

        // 绘制道路路径
        drawRoadPaths(ctx);

        // 绘制连接线（已完成的不显示，因为有实际道路）
        for (Records.StructureConnection conn : connections) {
            // 跳过已完成的连接
            if (conn.status() == Records.ConnectionStatus.COMPLETED) {
                continue;
            }
            
            ScreenPos a = screenPositions.get(conn.from());
            ScreenPos b = screenPositions.get(conn.to());
            if (a == null || b == null) continue;
            
            // 根据状态选择颜色 - 使用更鲜艳的颜色
            int color = switch (conn.status()) {
                case PLANNED -> 0xFFFFD700; // 金黄色
                case GENERATING -> 0xFFFF8C00; // 深橙色
                case COMPLETED -> statusColors.get("completed");
                case FAILED -> 0xFFFF4444; // 亮红色
            };
            
            drawLine(ctx, a.x, a.y, b.x, b.y, color);
        }

        // 绘制结构节点 - 更大更明显
        BlockPos hovered = null;
        for (BlockPos pos : structures) {
            ScreenPos p = screenPositions.get(pos);
            if (p == null) continue;
            
            // 外圈发光效果
            fillCircle(ctx, p.x, p.y, RADIUS + 2, 0x40FFFFFF);
            // 主体
            fillCircle(ctx, p.x, p.y, RADIUS, 0xFF2ECC71);
            // 高光
            fillCircle(ctx, p.x - 1, p.y - 1, 2, 0x8CFFFFFF);
            // 边框
            drawCircleOutline(ctx, p.x, p.y, RADIUS, 0xFF1E8449);

            if (dist2(p.x, p.y, mouseX, mouseY) <= (RADIUS + 2) * (RADIUS + 2)) {
                hovered = pos;
            }
        }

        // 绘制玩家位置
        drawPlayerMarker(ctx);

        // 绘制UI元素
        drawTitle(ctx);
        drawStatsPanel(ctx);
        drawLegendPanel(ctx);
        drawScalePanel(ctx);

        // 显示悬停提示 - 放在最后确保在最上层
        if (hovered != null) {
            drawTooltip(ctx, hovered, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawRoadPaths(DrawContext ctx) {
        if (roads == null || roads.isEmpty()) return;

        int roadColor = statusColors.get("road");
        
        for (Records.RoadData roadData : roads) {
            List<Records.RoadSegmentPlacement> segments = roadData.roadSegmentList();
            if (segments == null || segments.size() < 2) continue;

            // 绘制道路路径（连接中心点）
            for (int i = 0; i < segments.size() - 1; i++) {
                BlockPos pos1 = segments.get(i).middlePos();
                BlockPos pos2 = segments.get(i + 1).middlePos();
                
                ScreenPos p1 = worldToScreen(pos1.getX(), pos1.getZ());
                ScreenPos p2 = worldToScreen(pos2.getX(), pos2.getZ());
                
                // 使用半透明的蓝色绘制道路
                drawLine(ctx, p1.x, p1.y, p2.x, p2.y, (roadColor & 0x00FFFFFF) | 0x80000000);
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    protected void applyBlur(float delta) {
        // 禁用模糊效果
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // 点击节点传送
        BlockPos clicked = findClickedStructure(mouseX, mouseY);
        if (clicked != null) {
            teleportTo(clicked);
            return true;
        }
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) {
            offsetX += deltaX;
            offsetY += deltaY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double old = zoom;
        zoom = vertical > 0 ? zoom * 1.1 : zoom / 1.1;
        zoom = Math.max(0.1, Math.min(10.0, zoom)); // 限制缩放范围
        
        offsetX = (offsetX - mouseX + PADDING) * (zoom / old) + mouseX - PADDING;
        offsetY = (offsetY - mouseY + PADDING) * (zoom / old) + mouseY - PADDING;
        return true;
    }

    // 绘制标题栏
    private void drawTitle(DrawContext ctx) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        Text title = Text.translatable("gui.roadweaver.debug_map.title");
        int tw = font.getWidth(title);
        int x = (width - tw) / 2;
        int y = PADDING + 8;
        
        // 标题背景面板
        drawPanel(ctx, x - 10, y - 5, x + tw + 10, y + 14, 0xC0000000, 0xFF4A90E2);
        
        // 绘制标题文本 - 带阴影
        ctx.drawText(font, title, x, y, 0xFFFFFFFF, true);
    }

    // 绘制统计面板 - 右上角
    private void drawStatsPanel(DrawContext ctx) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        
        // 统计各状态的连接数
        int planned = 0;
        int generating = 0;
        int completed = 0;
        int failed = 0;
        for (Records.StructureConnection conn : connections) {
            switch (conn.status()) {
                case PLANNED -> planned++;
                case GENERATING -> generating++;
                case COMPLETED -> completed++;
                case FAILED -> failed++;
            }
        }
        
        // 准备显示文本
        String[] labels = {
            "结构: " + structures.size(),
            "计划中: " + planned,
            "生成中: " + generating,
            "已完成: " + completed,
            "失败: " + failed,
            "道路: " + roads.size(),
            "缩放: " + String.format("%.1fx", zoom)
        };
        
        int[] colors = {
            0xFFFFFFFF,
            0xFFFFD700, // 金黄色
            0xFFFF8C00, // 深橙色
            0xFF2ECC71, // 绿色
            0xFFFF4444, // 红色
            0xFF3498DB, // 蓝色
            0xFFBDC3C7  // 灰色
        };
        
        // 计算面板大小
        int maxWidth = 0;
        for (String label : labels) {
            maxWidth = Math.max(maxWidth, font.getWidth(label));
        }
        
        int panelWidth = maxWidth + 20;
        int panelHeight = labels.length * 14 + 10;
        int x = width - PADDING - panelWidth - 5;
        int y = PADDING + 30;
        
        // 绘制面板背景
        drawPanel(ctx, x, y, x + panelWidth, y + panelHeight, 0xD0000000, 0xFF34495E);
        
        // 绘制文本
        int textY = y + 5;
        for (int i = 0; i < labels.length; i++) {
            // 图标指示器
            ctx.fill(x + 5, textY + 2, x + 10, textY + 7, colors[i]);
            ctx.drawBorder(x + 5, textY + 2, 5, 5, 0x80FFFFFF);
            // 文本
            ctx.drawText(font, labels[i], x + 13, textY, colors[i], true);
            textY += 14;
        }
    }

    private void drawGrid(DrawContext ctx) {
        int w = width - PADDING * 2;
        int h = height - PADDING * 2;

        double worldX0 = minX + (-offsetX) / (baseScale * zoom);
        double worldZ0 = minZ + (-offsetY) / (baseScale * zoom);
        double worldX1 = minX + (w - offsetX) / (baseScale * zoom);
        double worldZ1 = minZ + (h - offsetY) / (baseScale * zoom);

        int spacing = computeGridSpacing();

        int startWX = (int) Math.floor(worldX0 / spacing) * spacing;
        int startWZ = (int) Math.floor(worldZ0 / spacing) * spacing;

        // 绘制垂直线
        for (int x = startWX; x <= worldX1; x += spacing) {
            int sx = PADDING + (int) ((x - worldX0) * baseScale * zoom);
            fillV(ctx, sx, PADDING, PADDING + h, 0x40444444);
            drawSmallLabel(ctx, String.valueOf(x), sx + 2, PADDING + 2);
        }

        // 绘制水平线
        for (int z = startWZ; z <= worldZ1; z += spacing) {
            int sz = PADDING + (int) ((z - worldZ0) * baseScale * zoom);
            fillH(ctx, PADDING, PADDING + w, sz, 0x40444444);
            drawSmallLabel(ctx, String.valueOf(z), PADDING + 2, sz + 2);
        }
    }

    // 绘制比例尺面板 - 右下角
    private void drawScalePanel(DrawContext ctx) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int spacing = computeGridSpacing();
        int lengthPx = (int) (spacing * baseScale * zoom);
        
        String text = spacing + " 方块";
        int textWidth = font.getWidth(text);
        int panelWidth = Math.max(lengthPx + 20, textWidth + 20);
        int panelHeight = 35;
        
        int x = width - PADDING - panelWidth - 5;
        int y = height - PADDING - panelHeight - 5;
        
        // 绘制面板背景
        drawPanel(ctx, x, y, x + panelWidth, y + panelHeight, 0xD0000000, 0xFF34495E);
        
        // 绘制比例尺
        int scaleX = x + (panelWidth - lengthPx) / 2;
        int scaleY = y + panelHeight - 10;
        
        // 比例尺线
        fillH(ctx, scaleX, scaleX + lengthPx, scaleY, 0xFFFFFFFF);
        fillV(ctx, scaleX, scaleY - 4, scaleY + 4, 0xFFFFFFFF);
        fillV(ctx, scaleX + lengthPx, scaleY - 4, scaleY + 4, 0xFFFFFFFF);
        
        // 文本
        ctx.drawText(font, text, x + (panelWidth - textWidth) / 2, y + 8, 0xFFFFFFFF, true);
    }

    // 绘制图例面板 - 左上角
    private void drawLegendPanel(DrawContext ctx) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        
        String[] labels = {
            "结构",
            "计划中",
            "生成中",
            "失败",
            "道路"
        };
        
        int[] colors = {
            0xFF2ECC71, // 绿色
            0xFFFFD700, // 金黄色
            0xFFFF8C00, // 深橙色
            0xFFFF4444, // 红色
            0xFF3498DB  // 蓝色
        };
        
        // 计算面板大小
        int maxWidth = 0;
        for (String label : labels) {
            maxWidth = Math.max(maxWidth, font.getWidth(label));
        }
        
        int panelWidth = maxWidth + 30;
        int panelHeight = labels.length * 16 + 10;
        int x = PADDING + 5;
        int y = PADDING + 30;
        
        // 绘制面板背景
        drawPanel(ctx, x, y, x + panelWidth, y + panelHeight, 0xD0000000, 0xFF34495E);
        
        // 绘制图例项
        int itemY = y + 5;
        for (int i = 0; i < labels.length; i++) {
            // 颜色指示器 - 圆形
            fillCircle(ctx, x + 10, itemY + 4, 4, colors[i]);
            drawCircleOutline(ctx, x + 10, itemY + 4, 4, 0x80FFFFFF);
            
            // 文本
            ctx.drawText(font, labels[i], x + 20, itemY, 0xFFFFFFFF, true);
            itemY += 16;
        }
    }

    private void computeLayout() {
        if (structures.isEmpty()) return;
        
        int w = Math.max(1, width - PADDING * 2);
        int h = Math.max(1, height - PADDING * 2);

        double scaleX = (double) w / Math.max(1, maxX - minX);
        double scaleZ = (double) h / Math.max(1, maxZ - minZ);
        baseScale = Math.min(scaleX, scaleZ) * 0.9; // 留一些边距

        if (firstLayout) {
            double scale = baseScale * zoom;
            // 以玩家为中心
            MinecraftClient mc = MinecraftClient.getInstance();
            double px = (mc != null && mc.player != null) ? mc.player.getX() : minX;
            double pz = (mc != null && mc.player != null) ? mc.player.getZ() : minZ;

            // 目标屏幕中心（面板内）
            double centerX = w / 2.0;
            double centerY = h / 2.0;

            // 设定偏移使玩家位于中心：PADDING + ((px - minX) * scale + offsetX) = PADDING + centerX
            offsetX = centerX - (px - minX) * scale;
            offsetY = centerY - (pz - minZ) * scale;

            firstLayout = false;
        }

        screenPositions.clear();
        for (BlockPos pos : structures) {
            double sx = (pos.getX() - minX) * baseScale * zoom + offsetX;
            double sy = (pos.getZ() - minZ) * baseScale * zoom + offsetY;
            screenPositions.put(pos, new ScreenPos(PADDING + (int) sx, PADDING + (int) sy));
        }
    }

    private int computeGridSpacing() {
        double unitsPerPixel = 1.0 / (baseScale * zoom);
        double raw = TARGET_GRID_PX * unitsPerPixel;
        double pow10 = Math.pow(10, Math.floor(Math.log10(raw)));
        
        for (int n : new int[]{1, 2, 5}) {
            double candidate = n * pow10;
            if (candidate >= raw) return (int) candidate;
        }
        return (int) (10 * pow10);
    }

    private BlockPos findClickedStructure(double mouseX, double mouseY) {
        for (BlockPos pos : structures) {
            ScreenPos p = screenPositions.get(pos);
            if (p != null && dist2(p.x, p.y, mouseX, mouseY) <= RADIUS * RADIUS) {
                return pos;
            }
        }
        return null;
    }

    private void teleportTo(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (mc.getServer() != null) {
            // 单人游戏：在服务器线程执行传送
            mc.getServer().execute(() -> {
                ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid());
                if (sp != null) {
                    sp.requestTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                }
            });
        }
    }

    private ScreenPos worldToScreen(double wx, double wz) {
        int sx = PADDING + (int) ((wx - minX) * baseScale * zoom + offsetX);
        int sy = PADDING + (int) ((wz - minZ) * baseScale * zoom + offsetY);
        return new ScreenPos(sx, sy);
    }

    private void drawPlayerMarker(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || structures.isEmpty()) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();

        ScreenPos p = worldToScreen(px, pz);

        // 红色玩家标记
        final int r = RADIUS + 2;
        final int fill = 0xFFE74C3C;
        final int outline = 0xFF000000;

        fillCircle(ctx, p.x, p.y, r, fill);
        drawCircleOutline(ctx, p.x, p.y, r, outline);

        // 方向箭头
        float yaw = mc.player.getYaw();
        double angle = Math.toRadians(yaw) + Math.PI / 2.0;
        int tx = p.x + (int) Math.round(Math.cos(angle) * (r + 4));
        int ty = p.y + (int) Math.round(Math.sin(angle) * (r + 4));
        drawLine(ctx, p.x, p.y, tx, ty, 0xFFFFFFFF);
    }

    private static double dist2(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private void drawSmallLabel(DrawContext ctx, String s, int x, int y) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        ctx.drawText(font, Text.literal(s), x, y, 0xFFFFFFFF, true);
    }

    // 绘制美化的面板
    private void drawPanel(DrawContext ctx, int x1, int y1, int x2, int y2, int bgColor, int borderColor) {
        // 背景
        ctx.fill(x1, y1, x2, y2, bgColor);
        // 边框
        ctx.drawBorder(x1, y1, x2 - x1, y2 - y1, borderColor);
        // 内部高光
        ctx.drawHorizontalLine(x1 + 1, x2 - 2, y1 + 1, 0x40FFFFFF);
        ctx.drawVerticalLine(x1 + 1, y1 + 1, y2 - 2, 0x40FFFFFF);
    }

    // 绘制美化的工具提示
    private void drawTooltip(DrawContext ctx, BlockPos pos, int mouseX, int mouseY) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        
        String[] lines = {
            "坐标: " + pos.getX() + ", " + pos.getZ(),
            "高度: Y " + pos.getY(),
            "点击传送"
        };
        
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.getWidth(line));
        }
        
        int tooltipWidth = maxWidth + 12;
        int tooltipHeight = lines.length * 11 + 6;
        
        // 调整位置避免超出屏幕
        int tx = mouseX + 10;
        int ty = mouseY + 10;
        if (tx + tooltipWidth > width - 5) tx = mouseX - tooltipWidth - 10;
        if (ty + tooltipHeight > height - 5) ty = mouseY - tooltipHeight - 10;
        
        // 绘制工具提示背景
        drawPanel(ctx, tx, ty, tx + tooltipWidth, ty + tooltipHeight, 0xF0000000, 0xFF4A90E2);
        
        // 绘制文本
        int textY = ty + 3;
        for (String line : lines) {
            ctx.drawText(font, line, tx + 6, textY, 0xFFFFFFFF, false);
            textY += 11;
        }
    }

    // ========== 绘图原语 ==========

    private static void fillH(DrawContext ctx, int x0, int x1, int y, int argb) {
        if (x1 < x0) {
            int t = x0;
            x0 = x1;
            x1 = t;
        }
        ctx.fill(x0, y, x1, y + 1, argb);
    }

    private static void fillV(DrawContext ctx, int x, int y0, int y1, int argb) {
        if (y1 < y0) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        ctx.fill(x, y0, x + 1, y1, argb);
    }

    private static void drawLine(DrawContext ctx, int x0, int y0, int x1, int y1, int argb) {
        // Bresenham 算法
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        
        while (true) {
            ctx.fill(x, y, x + 1, y + 1, argb);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void fillCircle(DrawContext ctx, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int span = (int) Math.round(Math.sqrt(r * r - dy * dy));
            ctx.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, argb);
        }
    }

    private static void drawCircleOutline(DrawContext ctx, int cx, int cy, int r, int argb) {
        int x = r;
        int y = 0;
        int err = 0;
        
        while (x >= y) {
            plot8(ctx, cx, cy, x, y, argb);
            y++;
            if (err <= 0) {
                err += 2 * y + 1;
            }
            if (err > 0) {
                x--;
                err -= 2 * x + 1;
            }
        }
    }

    private static void plot8(DrawContext ctx, int cx, int cy, int x, int y, int argb) {
        ctx.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, argb);
        ctx.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, argb);
        ctx.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, argb);
        ctx.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, argb);
        ctx.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, argb);
        ctx.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, argb);
        ctx.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, argb);
        ctx.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, argb);
    }

    private record ScreenPos(int x, int y) {}
}

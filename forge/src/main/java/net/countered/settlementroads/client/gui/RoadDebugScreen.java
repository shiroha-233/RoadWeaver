package net.countered.settlementroads.client.gui;

import net.countered.settlementroads.helpers.Records;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * 道路网络调试屏幕 - Forge版本（重构版）
 * 功能: 显示结构节点、道路连接、支持平移/缩放、点击传送
 * 包含LOD系统、高级渲染、性能优化
 * 
 * 修复内容：
 * 1. 计划道路现在正确连接结构位置（使用虚线）
 * 2. 优化LOD系统，改善缩小时的精度
 * 3. 模块化代码结构，便于维护
 */
public class RoadDebugScreen extends Screen {

    private static final int PADDING = 20;

    private final List<BlockPos> structures;
    private final List<Records.StructureConnection> connections;
    private final List<Records.RoadData> roads;

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
    private boolean layoutDirty = true;
    private double zoom = 3.0;
    private double offsetX = 0;
    private double offsetY = 0;
    private double baseScale = 1.0;
    private int minX, maxX, minZ, maxZ;
    
    // 性能优化缓存
    private int lastWidth = 0;
    private int lastHeight = 0;
    private double lastZoom = 1.0;
    private double lastOffsetX = 0;
    private double lastOffsetY = 0;
    
    private BlockPos hoveredStructure = null;
    
    // 渲染器
    private final MapRenderer mapRenderer;
    private final GridRenderer gridRenderer;
    private final UIRenderer uiRenderer;
    private final ScreenBounds bounds;

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
        
        // 初始化渲染器
        this.bounds = new ScreenBounds();
        this.mapRenderer = new MapRenderer(statusColors, bounds);
        this.gridRenderer = new GridRenderer();
        this.uiRenderer = new UIRenderer(statusColors);
    }
    
    private void computeLayout() {
        if (structures.isEmpty()) {
            baseScale = 1.0;
            return;
        }
        
        int w = width - PADDING * 2;
        int h = height - PADDING * 2;
        
        if (w <= 0 || h <= 0) return;
        
        int worldW = maxX - minX;
        int worldH = maxZ - minZ;
        
        if (worldW <= 0 || worldH <= 0) {
            baseScale = 1.0;
            return;
        }
        
        double scaleX = (double) w / worldW;
        double scaleY = (double) h / worldH;
        baseScale = Math.min(scaleX, scaleY) * 0.8;
        
        if (firstLayout) {
            offsetX = (w - worldW * baseScale * zoom) / 2;
            offsetY = (h - worldH * baseScale * zoom) / 2;
            firstLayout = false;
        }
        
        layoutDirty = false;
    }
    
    private void updateUIBounds() {
        bounds.update(PADDING, width - PADDING, PADDING, height - PADDING);
    }
    
    private ScreenPos worldToScreen(double worldX, double worldZ) {
        int x = PADDING + (int) ((worldX - minX) * baseScale * zoom + offsetX);
        int y = PADDING + (int) ((worldZ - minZ) * baseScale * zoom + offsetY);
        return new ScreenPos(x, y);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // 只有在必要时才重新计算布局和UI边界
        if (layoutDirty || lastWidth != width || lastHeight != height || 
            lastZoom != zoom || lastOffsetX != offsetX || lastOffsetY != offsetY) {
            computeLayout();
            updateUIBounds();
            lastWidth = width;
            lastHeight = height;
            lastZoom = zoom;
            lastOffsetX = offsetX;
            lastOffsetY = offsetY;
            layoutDirty = false;
        }
        
        this.renderCustomBackground(ctx);
        
        // 获取当前LOD级别
        MapRenderer.LODLevel lod = mapRenderer.getLODLevel(zoom);
        
        // 创建坐标转换器
        MapRenderer.WorldToScreenConverter converter = this::worldToScreen;
        
        // 根据LOD级别绘制各种元素
        if (lod != MapRenderer.LODLevel.MINIMAL) {
            gridRenderer.drawGrid(ctx, lod, width, height, PADDING, 
                baseScale, zoom, offsetX, offsetY, minX, minZ, bounds);
        }
        
        // 绘制道路路径（实际路径）
        mapRenderer.drawRoadPaths(ctx, roads, lod, baseScale, zoom, converter);
        
        // 绘制连接线（计划中的道路显示为虚线）
        mapRenderer.drawConnections(ctx, connections, roads, lod, converter);
        
        // 绘制结构节点
        mapRenderer.drawStructures(ctx, structures, hoveredStructure, lod, converter);
        
        // 绘制玩家标记
        mapRenderer.drawPlayerMarker(ctx, lod, zoom, converter);
        
        // UI元素
        uiRenderer.drawTitle(ctx, width);
        uiRenderer.drawStatsPanel(ctx, width, structures, connections, roads, zoom);
        uiRenderer.drawLegendPanel(ctx, height);
        
        if (hoveredStructure != null) {
            uiRenderer.drawTooltip(ctx, hoveredStructure, mouseX, mouseY, width);
        }
        
        updateHoveredStructure(mouseX, mouseY);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
    
    private void renderCustomBackground(GuiGraphics ctx) {
        // 渐变背景
        ctx.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            offsetX += dragX;
            offsetY += dragY;
            layoutDirty = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        double old = zoom;
        zoom = vertical > 0 ? zoom * 1.1 : zoom / 1.1;
        zoom = Math.max(0.1, Math.min(10.0, zoom));
        
        offsetX = (offsetX - mouseX + PADDING) * (zoom / old) + mouseX - PADDING;
        offsetY = (offsetY - mouseY + PADDING) * (zoom / old) + mouseY - PADDING;
        
        layoutDirty = true;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        BlockPos clicked = findClickedStructure(mouseX, mouseY);
        if (clicked != null) {
            teleportTo(clicked);
            return true;
        }
        dragging = true;
        return true;
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    private void updateHoveredStructure(int mouseX, int mouseY) {
        hoveredStructure = findClickedStructure(mouseX, mouseY);
    }
    
    private BlockPos findClickedStructure(double mouseX, double mouseY) {
        for (BlockPos structure : structures) {
            ScreenPos pos = worldToScreen(structure.getX(), structure.getZ());
            double dx = pos.x - mouseX;
            double dy = pos.y - mouseY;
            if (Math.sqrt(dx * dx + dy * dy) <= 5 + 2) {
                return structure;
            }
        }
        return null;
    }
    
    private void teleportTo(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.hasSingleplayerServer()) return;
        
        Player player = mc.player;
        String command = "/tp " + player.getName().getString() + " " + pos.getX() + " ~ " + pos.getZ();
        
        if (mc.getSingleplayerServer() != null) {
            mc.getSingleplayerServer().getCommands().performPrefixedCommand(
                mc.getSingleplayerServer().createCommandSourceStack(), command);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    /**
     * 屏幕坐标记录
     */
    public record ScreenPos(int x, int y) {}
    
    /**
     * 屏幕边界类
     */
    public static class ScreenBounds {
        private int left, right, top, bottom;
        
        public void update(int left, int right, int top, int bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
        
        public boolean isInBounds(int x, int y, int margin) {
            return x >= left - margin && x <= right + margin && 
                   y >= top - margin && y <= bottom + margin;
        }
        
        public boolean isLineInBounds(int x1, int y1, int x2, int y2) {
            if ((x1 < left && x2 < left) || (x1 > right && x2 > right) ||
                (y1 < top && y2 < top) || (y1 > bottom && y2 > bottom)) {
                return false;
            }
            return true;
        }
        
        public int left() { return left; }
        public int right() { return right; }
        public int top() { return top; }
        public int bottom() { return bottom; }
    }
}

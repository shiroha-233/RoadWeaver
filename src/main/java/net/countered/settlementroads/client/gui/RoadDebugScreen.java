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

    private static final int RADIUS = 6;  // 增加节点半径使其更清晰
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
    private boolean layoutDirty = true;  // 🆕 布局缓存标记
    private double zoom = 3.0;
    private double offsetX = 0;
    private double offsetY = 0;
    private double baseScale = 1.0;
    private int minX, maxX, minZ, maxZ;
    
    // 🆕 性能优化缓存
    private int lastWidth = 0;
    private int lastHeight = 0;
    private double lastZoom = 1.0;
    private double lastOffsetX = 0;
    private double lastOffsetY = 0;
    
    // 🆕 LOD 系统配置
    private static final double LOD_DISTANCE_1 = 0.3;  // 高细节阈值
    private static final double LOD_DISTANCE_2 = 1.0;  // 中等细节阈值
    private static final double LOD_DISTANCE_3 = 3.0;  // 低细节阈值
    
    // 🆕 道路渲染LOD配置 - 基于块/格比例 (极度激进的设置)
    private static final double ROAD_LOD_FINEST = 300;      // < 300: 全精度渲染
    private static final double ROAD_LOD_64TH = 300;        // >= 300: 1/64精度
    private static final double ROAD_LOD_128TH = 500;       // >= 500: 1/128精度
    private static final double ROAD_LOD_256TH = 1000;      // >= 1000: 1/256精度
    private static final double ROAD_LOD_512TH = 2000;      // >= 2000: 1/512精度
    private static final double ROAD_LOD_1024TH = 5000;     // >= 5000: 1/1024精度
    private static final double ROAD_LOD_NONE = 10000;      // >= 10000: 不渲染
    
    // UI边界缓存
    private int uiLeft, uiRight, uiTop, uiBottom;
    private boolean uiBoundsDirty = true;

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
        // 🆕 只有在必要时才重新计算布局和UI边界
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
            uiBoundsDirty = false;
        }

        // 绘制主背景面板 - 深色半透明
        drawPanel(ctx, PADDING, PADDING, width - PADDING, height - PADDING, 0xE0101010, 0xFF2C2C2C);

        // 🆕 根据LOD级别决定是否绘制网格
        LODLevel lodLevel = getLODLevel();
        if (lodLevel != LODLevel.MINIMAL) {
            drawGrid(ctx);
        }

        // 🆕 使用LOD系统绘制道路路径
        drawRoadPathsLOD(ctx, lodLevel);

        // 🆕 使用LOD系统绘制连接线
        if (lodLevel != LODLevel.MINIMAL) {
            for (Records.StructureConnection conn : connections) {
                // 跳过已完成的连接
                if (conn.status() == Records.ConnectionStatus.COMPLETED) {
                    continue;
                }
                
                ScreenPos a = screenPositions.get(conn.from());
                ScreenPos b = screenPositions.get(conn.to());
                if (a == null || b == null) continue;
                
                // 🆕 连接线也需要边界检查
                if (!isLineInUIBounds(a.x, a.y, b.x, b.y)) {
                    continue;
                }
                
                // 根据状态选择颜色 - 使用更鲜艳的颜色
                int color = switch (conn.status()) {
                    case PLANNED -> 0xFFFFD700; // 金黄色
                    case GENERATING -> 0xFFFF8C00; // 深橙色
                    case COMPLETED -> statusColors.get("completed");
                    case FAILED -> 0xFFFF4444; // 亮红色
                };
                
                drawLine(ctx, a.x, a.y, b.x, b.y, color);
            }
        }

        // 🆕 使用LOD系统的结构节点绘制
        BlockPos hovered = null;
        for (BlockPos pos : structures) {
            ScreenPos p = screenPositions.get(pos);
            if (p == null) continue;
            
            // 🆕 严格的UI边界剔除 - 使用自适应半径
            int nodeRadius = getAdaptiveNodeRadius(lodLevel);
            if (!isInUIBounds(p.x, p.y, nodeRadius + 2)) {
                continue;
            }
            
            // 🆕 根据LOD级别绘制不同精度的节点
            drawStructureNodeLOD(ctx, p.x, p.y, lodLevel);

            // 🆕 鼠标悬停检测也要使用自适应半径
            if (dist2(p.x, p.y, mouseX, mouseY) <= (nodeRadius + 2) * (nodeRadius + 2)) {
                hovered = pos;
            }
        }

        // 绘制玩家位置
        drawPlayerMarkerLOD(ctx, lodLevel);

        // 🆕 UI元素根据LOD级别决定是否显示
        drawTitle(ctx);
        drawStatsPanel(ctx);
        if (lodLevel == LODLevel.HIGH || lodLevel == LODLevel.MEDIUM) {
            drawLegendPanel(ctx);
            drawScalePanel(ctx);
        }

        // 显示悬停提示 - 放在最后确保在最上层
        if (hovered != null) {
            drawTooltip(ctx, hovered, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // 🆕 LOD级别枚举
    private enum LODLevel {
        HIGH,    // zoom > 3.0 - 完整细节
        MEDIUM,  // 1.0 < zoom <= 3.0 - 中等细节  
        LOW,     // 0.3 < zoom <= 1.0 - 低细节
        MINIMAL  // zoom <= 0.3 - 最少细节
    }
    
    // 🆕 道路渲染LOD级别枚举 (极度激进设置)
    private enum RoadLODLevel {
        FINEST,       // < 300块/格 - 全精度渲染 (步长1)
        SIXTY_FOURTH, // 300-500块/格 - 1/64精度 (步长64)
        ONE_TWENTY_EIGHTH, // 500-1000块/格 - 1/128精度 (步长128)
        TWO_FIFTY_SIXTH, // 1000-2000块/格 - 1/256精度 (步长256)
        FIVE_TWELVE, // 2000-5000块/格 - 1/512精度 (步长512)
        ONE_THOUSAND_TWENTY_FOURTH, // 5000-10000块/格 - 1/1024精度 (步长1024)
        NONE          // > 10000块/格 - 不渲染
    }
    
    private LODLevel getLODLevel() {
        if (zoom > LOD_DISTANCE_3) return LODLevel.HIGH;
        if (zoom > LOD_DISTANCE_2) return LODLevel.MEDIUM;
        if (zoom > LOD_DISTANCE_1) return LODLevel.LOW;
        return LODLevel.MINIMAL;
    }
    
    /**
     * 🆕 计算道路渲染LOD级别
     * 基于当前缩放比例下的块/格比例
     * @return 道路LOD级别
     */
    private RoadLODLevel getRoadLODLevel() {
        // 计算当前缩放下，每个屏幕像素代表多少个世界方块
        double blocksPerPixel = 1.0 / (baseScale * zoom);
        
        // 计算每格（假设格子间距为TARGET_GRID_PX像素）代表多少方块
        double blocksPerGrid = blocksPerPixel * TARGET_GRID_PX;
        
        // 根据块/格比例确定LOD级别（从最精细开始判断）
        if (blocksPerGrid < ROAD_LOD_FINEST) return RoadLODLevel.FINEST;           // < 300
        if (blocksPerGrid < ROAD_LOD_128TH) return RoadLODLevel.SIXTY_FOURTH;      // 300-500
        if (blocksPerGrid < ROAD_LOD_256TH) return RoadLODLevel.ONE_TWENTY_EIGHTH; // 500-1000
        if (blocksPerGrid < ROAD_LOD_512TH) return RoadLODLevel.TWO_FIFTY_SIXTH;   // 1000-2000
        if (blocksPerGrid < ROAD_LOD_1024TH) return RoadLODLevel.FIVE_TWELVE;      // 2000-5000
        if (blocksPerGrid < ROAD_LOD_NONE) return RoadLODLevel.ONE_THOUSAND_TWENTY_FOURTH; // 5000-10000
        return RoadLODLevel.NONE;  // >= 10000
    }
    
    // 🆕 更新UI边界
    private void updateUIBounds() {
        uiLeft = PADDING;
        uiRight = width - PADDING;
        uiTop = PADDING;
        uiBottom = height - PADDING;
    }
    
    // 🆕 检查点是否在UI边界内
    private boolean isInUIBounds(int x, int y, int margin) {
        return x >= uiLeft - margin && x <= uiRight + margin && 
               y >= uiTop - margin && y <= uiBottom + margin;
    }
    
    // 🆕 检查线段是否与UI边界相交
    private boolean isLineInUIBounds(int x1, int y1, int x2, int y2) {
        // 简化版边界检查 - 如果两个端点都在边界外的同一侧，则跳过
        if ((x1 < uiLeft && x2 < uiLeft) || (x1 > uiRight && x2 > uiRight) ||
            (y1 < uiTop && y2 < uiTop) || (y1 > uiBottom && y2 > uiBottom)) {
            return false;
        }
        return true;
    }
    
    // 🆕 LOD系统的道路绘制 - 基于缩放比例
    private void drawRoadPathsLOD(DrawContext ctx, LODLevel lod) {
        if (roads == null || roads.isEmpty()) return;
        if (lod == LODLevel.MINIMAL) return; // 最小LOD不绘制道路

        // 🆕 获取道路专用的LOD级别
        RoadLODLevel roadLOD = getRoadLODLevel();
        if (roadLOD == RoadLODLevel.NONE) return; // 超过10000块/格，不渲染道路

        int roadColor = (statusColors.get("road") & 0x00FFFFFF) | 0x80000000;
        
        // 🆕 在高LOD级别下进行粗略的整条道路边界检查
        boolean needsRoughCheck = (roadLOD == RoadLODLevel.FIVE_TWELVE || 
                                  roadLOD == RoadLODLevel.ONE_THOUSAND_TWENTY_FOURTH || 
                                  roadLOD == RoadLODLevel.NONE);
        
        for (Records.RoadData roadData : roads) {
            List<Records.RoadSegmentPlacement> segments = roadData.roadSegmentList();
            if (segments == null || segments.size() < 2) continue;

            // 🆕 粗略检查：如果道路的起点和终点都在屏幕外，跳过整条道路
            if (needsRoughCheck && segments.size() > 1) {
                BlockPos start = segments.get(0).middlePos();
                BlockPos end = segments.get(segments.size() - 1).middlePos();
                ScreenPos startScreen = worldToScreen(start.getX(), start.getZ());
                ScreenPos endScreen = worldToScreen(end.getX(), end.getZ());
                
                // 如果起点和终点都在UI边界外且在同一侧，跳过这条道路
                if (!isInUIBounds(startScreen.x, startScreen.y, 200) && 
                    !isInUIBounds(endScreen.x, endScreen.y, 200)) {
                    continue;
                }
            }

            // 🆕 使用道路专用LOD级别调整采样率
            drawRoadPathWithRoadLOD(ctx, segments, roadColor, roadLOD);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;  // 不暂停游戏
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 不渲染任何背景，保持透明
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
            layoutDirty = true; // 🆕 标记需要重新计算布局
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
        
        layoutDirty = true; // 🆕 标记需要重新计算布局
        uiBoundsDirty = true; // 🆕 标记需要更新UI边界
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

    // 🆕 网格标签缓存避免重复创建字符串
    private final Map<Integer, String> gridLabelCache = new HashMap<>();

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

        // 🆕 只有在需要时才清理和重新计算屏幕位置
        if (screenPositions.isEmpty() || layoutDirty) {
            screenPositions.clear();
            for (BlockPos pos : structures) {
                double sx = (pos.getX() - minX) * baseScale * zoom + offsetX;
                double sy = (pos.getZ() - minZ) * baseScale * zoom + offsetY;
                screenPositions.put(pos, new ScreenPos(PADDING + (int) sx, PADDING + (int) sy));
            }
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

    // 已替换为 drawPlayerMarkerLOD 方法

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
        // 使用加粗的 Bresenham 算法绘制 2 像素宽的线条
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        
        while (true) {
            // 绘制 2x2 像素块使线条更粗更清晰
            ctx.fill(x, y, x + 2, y + 2, argb);
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

    // 🆕 优化的圆形绘制 - 预计算避免重复 sqrt
    private static final int[][] CIRCLE_CACHE = new int[20][];
    static {
        for (int r = 0; r < 20; r++) {
            CIRCLE_CACHE[r] = new int[r * 2 + 1];
            for (int dy = -r; dy <= r; dy++) {
                CIRCLE_CACHE[r][dy + r] = (int) Math.round(Math.sqrt(r * r - dy * dy));
            }
        }
    }
    
    private static void fillCircle(DrawContext ctx, int cx, int cy, int r, int argb) {
        if (r < CIRCLE_CACHE.length) {
            // 🆕 使用预计算的值
            int[] spans = CIRCLE_CACHE[r];
            for (int dy = -r; dy <= r; dy++) {
                int span = spans[dy + r];
                ctx.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, argb);
            }
        } else {
            // 回退到原始方法
            for (int dy = -r; dy <= r; dy++) {
                int span = (int) Math.round(Math.sqrt(r * r - dy * dy));
                ctx.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, argb);
            }
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

    // 🆕 根据LOD级别和缩放级别自适应绘制结构节点 - 精美版
    private void drawStructureNodeLOD(DrawContext ctx, int x, int y, LODLevel lod) {
        int adaptiveRadius = getAdaptiveNodeRadius(lod);
        
        // 防止节点过小不可见
        if (adaptiveRadius < 2) {
            // 最小的像素点
            ctx.fill(x - 1, y - 1, x + 2, y + 2, 0xFF2ECC71);
            return;
        }
        
        switch (lod) {
            case HIGH -> {
                // 🆕 高细节：精美的多层绘制
                // 柔和的外发光（更细的颜色）
                fillCircle(ctx, x, y, adaptiveRadius + 1, 0x30A3E635); // 柔和的绿色发光
                // 主体圆形
                fillCircle(ctx, x, y, adaptiveRadius, 0xFF2ECC71);
                // 深色边框
                drawCircleOutline(ctx, x, y, adaptiveRadius, 0xFF1E8449);
                // 小巧的高光
                int highlightSize = Math.max(1, adaptiveRadius / 4);
                ctx.fill(x - highlightSize, y - highlightSize, 
                        x + highlightSize + 1, y + highlightSize + 1, 0xAAFFFFFF);
            }
            case MEDIUM -> {
                // 🆕 中等细节：简洁优雅
                fillCircle(ctx, x, y, adaptiveRadius, 0xFF2ECC71);
                drawCircleOutline(ctx, x, y, adaptiveRadius, 0xFF1E8449);
                // 小的高光点
                ctx.fill(x - 1, y - 1, x + 1, y + 1, 0x88FFFFFF);
            }
            case LOW -> {
                // 🆕 低细节：纯粹的圆形
                fillCircle(ctx, x, y, adaptiveRadius, 0xFF2ECC71);
                if (adaptiveRadius >= 3) {
                    drawCircleOutline(ctx, x, y, adaptiveRadius, 0xFF1E8449);
                }
            }
            case MINIMAL -> {
                // 🆕 最小细节：简单但清晰
                if (adaptiveRadius <= 2) {
                    ctx.fill(x - 1, y - 1, x + 2, y + 2, 0xFF2ECC71);
                } else {
                    fillCircle(ctx, x, y, adaptiveRadius, 0xFF2ECC71);
                }
            }
        }
    }
    
    // 🆕 计算自适应节点大小 - 优化版
    private int getAdaptiveNodeRadius(LODLevel lod) {
        // 🆕 更温和的缩放算法，防止过大
        double baseRadius = RADIUS;
        // 使用对数函数让放大时增长更缓慢，最大限制在1.2倍
        double zoomFactor = Math.max(0.3, Math.min(1.2, 1.0 + Math.log10(zoom) * 0.15));
        double scaledRadius = baseRadius * zoomFactor;
        
        // 根据LOD级别进一步调整
        double lodMultiplier = switch (lod) {
            case HIGH -> 0.9;    // 高细节（放大时）反而更小
            case MEDIUM -> 1.0;  // 正常大小
            case LOW -> 0.8;     // 低细节稍小
            case MINIMAL -> 0.6; // 最小细节很小
        };
        
        return Math.max(2, (int) Math.round(scaledRadius * lodMultiplier));
    }
    
    // 🆕 LOD系统的玩家标记 - 自适应大小 精美版
    private void drawPlayerMarkerLOD(DrawContext ctx, LODLevel lod) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || structures.isEmpty()) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        ScreenPos p = worldToScreen(px, pz);
        
        // 🆕 玩家标记比结构节点略大，但不过大
        int playerRadius = Math.max(3, getAdaptiveNodeRadius(lod) + 1);
        
        // 检查是否在UI边界内
        if (!isInUIBounds(p.x, p.y, playerRadius + 6)) return;

        final int fill = 0xFFE74C3C;
        final int glow = 0x40E74C3C;
        final int outline = 0xFF932D1F;

        switch (lod) {
            case HIGH -> {
                // 🆕 高细节：精美的玩家标记
                // 柔和的红色发光
                fillCircle(ctx, p.x, p.y, playerRadius + 1, glow);
                // 主体
                fillCircle(ctx, p.x, p.y, playerRadius, fill);
                // 边框
                drawCircleOutline(ctx, p.x, p.y, playerRadius, outline);
                // 小巧的高光
                int highlightSize = Math.max(1, playerRadius / 4);
                ctx.fill(p.x - highlightSize, p.y - highlightSize, 
                        p.x + highlightSize + 1, p.y + highlightSize + 1, 0xAAFFFFFF);
                // 方向箭头
                float yaw = mc.player.getYaw();
                double angle = Math.toRadians(yaw) + Math.PI / 2.0;
                int arrowLength = playerRadius + 3;
                int tx = p.x + (int) Math.round(Math.cos(angle) * arrowLength);
                int ty = p.y + (int) Math.round(Math.sin(angle) * arrowLength);
                drawLine(ctx, p.x, p.y, tx, ty, 0xFFFFFFFF);
            }
            case MEDIUM -> {
                // 🆕 中等细节：简洁优雅
                fillCircle(ctx, p.x, p.y, playerRadius, fill);
                drawCircleOutline(ctx, p.x, p.y, playerRadius, outline);
                // 小的高光点
                ctx.fill(p.x - 1, p.y - 1, p.x + 1, p.y + 1, 0x88FFFFFF);
            }
            case LOW -> {
                // 🆕 低细节：纯粹的圆形
                fillCircle(ctx, p.x, p.y, playerRadius, fill);
                if (playerRadius >= 4) {
                    drawCircleOutline(ctx, p.x, p.y, playerRadius, outline);
                }
            }
            case MINIMAL -> {
                // 🆕 最小细节：简单但清晰
                fillCircle(ctx, p.x, p.y, Math.max(2, playerRadius), fill);
            }
        }
    }
    
    /**
     * 🆕 根据道路LOD级别绘制道路路径
     * 基于缩放比例动态调整渲染精度
     * 
     * @param ctx 绘制上下文
     * @param segments 道路路段列表
     * @param color 道路颜色
     * @param roadLOD 道路LOD级别
     */
    private void drawRoadPathWithRoadLOD(DrawContext ctx, List<Records.RoadSegmentPlacement> segments, int color, RoadLODLevel roadLOD) {
        // 🆕 根据道路LOD级别决定采样步长
        int step = switch (roadLOD) {
            case FINEST -> 1;                           // 全精度渲染
            case SIXTY_FOURTH -> 64;                    // 1/64精度
            case ONE_TWENTY_EIGHTH -> 128;              // 1/128精度
            case TWO_FIFTY_SIXTH -> 256;                // 1/256精度
            case FIVE_TWELVE -> 512;                    // 1/512精度
            case ONE_THOUSAND_TWENTY_FOURTH -> 1024;    // 1/1024精度
            case NONE -> Integer.MAX_VALUE;             // 不渲染（理论上不会到这里）
        };
        
        if (step >= segments.size()) return; // 步长太大，跳过
        
        ScreenPos prevPos = null;
        int drawnSegments = 0;
        int maxSegments = 10000; // 防止过度渲染
        
        for (int i = 0; i < segments.size() && drawnSegments < maxSegments; i += step) {
            BlockPos pos = segments.get(i).middlePos();
            ScreenPos currentPos = worldToScreen(pos.getX(), pos.getZ());
            
            // 🆕 边界检查优化 - 扩大边界以避免线段被截断
            if (!isInUIBounds(currentPos.x, currentPos.y, 100)) {
                prevPos = currentPos;
                continue;
            }
            
            if (prevPos != null && i > 0) {
                if (isLineInUIBounds(prevPos.x, prevPos.y, currentPos.x, currentPos.y)) {
                    drawLine(ctx, prevPos.x, prevPos.y, currentPos.x, currentPos.y, color);
                    drawnSegments++;
                }
            }
            prevPos = currentPos;
        }
    }
    
    // 🆕 优化的网格绘制 - 根据LOD调整
    private void drawGrid(DrawContext ctx) {
        LODLevel lod = getLODLevel();
        if (lod == LODLevel.MINIMAL) return; // 最小LOD不绘制网格
        
        int w = width - PADDING * 2;
        int h = height - PADDING * 2;

        double worldX0 = minX + (-offsetX) / (baseScale * zoom);
        double worldZ0 = minZ + (-offsetY) / (baseScale * zoom);
        double worldX1 = minX + (w - offsetX) / (baseScale * zoom);
        double worldZ1 = minZ + (h - offsetY) / (baseScale * zoom);

        int spacing = computeGridSpacing();

        int startWX = (int) Math.floor(worldX0 / spacing) * spacing;
        int startWZ = (int) Math.floor(worldZ0 / spacing) * spacing;

        // 🆕 根据LOD级别调整网格线数量和标签显示
        int maxGridLines = switch (lod) {
            case HIGH -> 100;
            case MEDIUM -> 50;
            case LOW -> 25;
            case MINIMAL -> 0;
        };
        
        boolean showLabels = lod == LODLevel.HIGH || lod == LODLevel.MEDIUM;
        int gridLineCount = 0;

        // 绘制垂直线
        for (int x = startWX; x <= worldX1 && gridLineCount < maxGridLines; x += spacing) {
            int sx = PADDING + (int) ((x - worldX0) * baseScale * zoom);
            if (sx >= uiLeft && sx <= uiRight) {
                fillV(ctx, sx, uiTop, uiBottom, 0x40444444);
                if (showLabels) {
                    String label = gridLabelCache.computeIfAbsent(x, String::valueOf);
                    drawSmallLabel(ctx, label, sx + 2, uiTop + 2);
                }
                gridLineCount++;
            }
        }

        // 绘制水平线
        gridLineCount = 0;
        for (int z = startWZ; z <= worldZ1 && gridLineCount < maxGridLines; z += spacing) {
            int sz = PADDING + (int) ((z - worldZ0) * baseScale * zoom);
            if (sz >= uiTop && sz <= uiBottom) {
                fillH(ctx, uiLeft, uiRight, sz, 0x40444444);
                if (showLabels) {
                    String label = gridLabelCache.computeIfAbsent(z, String::valueOf);
                    drawSmallLabel(ctx, label, uiLeft + 2, sz + 2);
                }
                gridLineCount++;
            }
        }
    }

    private record ScreenPos(int x, int y) {}
}

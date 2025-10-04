package net.countered.settlementroads.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * 地图渲染器 - 负责绘制道路、连接线、结构等元素
 */
public class MapRenderer {
    
    // LOD级别枚举
    public enum LODLevel {
        HIGH,     // 高细节级别
        MEDIUM,   // 中等细节级别  
        LOW,      // 低细节级别
        MINIMAL   // 最小细节级别
    }
    
    // 道路LOD级别枚举 - 优化后的阈值
    public enum RoadLODLevel {
        FINEST,                    // < 150块/格 - 全精度渲染 (步长1)
        THIRTY_SECOND,             // 150-300块/格 - 1/32精度 (步长32)
        SIXTY_FOURTH,              // 300-600块/格 - 1/64精度 (步长64)
        ONE_TWENTY_EIGHTH,         // 600-1200块/格 - 1/128精度 (步长128)
        TWO_FIFTY_SIXTH,           // 1200-2500块/格 - 1/256精度 (步长256)
        FIVE_TWELVE,               // 2500-5000块/格 - 1/512精度 (步长512)
        ONE_THOUSAND_TWENTY_FOURTH, // 5000-10000块/格 - 1/1024精度 (步长1024)
        NONE                       // > 10000块/格 - 不渲染
    }
    
    private static final int RADIUS = 5;
    private static final int TARGET_GRID_PX = 80;
    
    // 道路渲染LOD配置 - 优化后的阈值
    private static final double ROAD_LOD_FINEST = 150;      // < 150: 全精度渲染
    private static final double ROAD_LOD_32ND = 150;        // >= 150: 1/32精度
    private static final double ROAD_LOD_64TH = 300;        // >= 300: 1/64精度
    private static final double ROAD_LOD_128TH = 600;       // >= 600: 1/128精度
    private static final double ROAD_LOD_256TH = 1200;      // >= 1200: 1/256精度
    private static final double ROAD_LOD_512TH = 2500;      // >= 2500: 1/512精度
    private static final double ROAD_LOD_1024TH = 5000;     // >= 5000: 1/1024精度
    private static final double ROAD_LOD_NONE = 10000;      // >= 10000: 不渲染
    
    // LOD系统配置
    private static final double LOD_DISTANCE_1 = 0.3;  // 高细节阈值
    private static final double LOD_DISTANCE_2 = 1.0;  // 中等细节阈值
    private static final double LOD_DISTANCE_3 = 3.0;  // 低细节阈值
    
    private final Map<String, Integer> statusColors;
    private final RoadDebugScreen.ScreenBounds bounds;
    
    public MapRenderer(Map<String, Integer> statusColors, RoadDebugScreen.ScreenBounds bounds) {
        this.statusColors = statusColors;
        this.bounds = bounds;
    }
    
    public LODLevel getLODLevel(double zoom) {
        if (zoom > LOD_DISTANCE_3) return LODLevel.HIGH;
        if (zoom > LOD_DISTANCE_2) return LODLevel.MEDIUM;
        if (zoom > LOD_DISTANCE_1) return LODLevel.LOW;
        return LODLevel.MINIMAL;
    }
    
    /**
     * 计算道路渲染LOD级别
     * 基于当前缩放比例下的块/格比例
     */
    public RoadLODLevel getRoadLODLevel(double baseScale, double zoom) {
        double blocksPerPixel = 1.0 / (baseScale * zoom);
        double blocksPerGrid = blocksPerPixel * TARGET_GRID_PX;
        
        if (blocksPerGrid < ROAD_LOD_FINEST) return RoadLODLevel.FINEST;
        if (blocksPerGrid < ROAD_LOD_64TH) return RoadLODLevel.THIRTY_SECOND;
        if (blocksPerGrid < ROAD_LOD_128TH) return RoadLODLevel.SIXTY_FOURTH;
        if (blocksPerGrid < ROAD_LOD_256TH) return RoadLODLevel.ONE_TWENTY_EIGHTH;
        if (blocksPerGrid < ROAD_LOD_512TH) return RoadLODLevel.TWO_FIFTY_SIXTH;
        if (blocksPerGrid < ROAD_LOD_1024TH) return RoadLODLevel.FIVE_TWELVE;
        if (blocksPerGrid < ROAD_LOD_NONE) return RoadLODLevel.ONE_THOUSAND_TWENTY_FOURTH;
        return RoadLODLevel.NONE;
    }
    
    /**
     * 绘制道路路径 - 使用实际的道路段数据
     */
    public void drawRoadPaths(GuiGraphics ctx, List<Records.RoadData> roads, 
                             LODLevel lod, double baseScale, double zoom,
                             WorldToScreenConverter converter) {
        if (roads == null || roads.isEmpty()) return;
        if (lod == LODLevel.MINIMAL) return;

        RoadLODLevel roadLOD = getRoadLODLevel(baseScale, zoom);
        if (roadLOD == RoadLODLevel.NONE) return;

        int roadColor = (statusColors.get("road") & 0x00FFFFFF) | 0x80000000;
        
        boolean needsRoughCheck = (roadLOD == RoadLODLevel.FIVE_TWELVE || 
                                  roadLOD == RoadLODLevel.ONE_THOUSAND_TWENTY_FOURTH);
        
        for (Records.RoadData roadData : roads) {
            List<Records.RoadSegmentPlacement> segments = roadData.roadSegmentList();
            if (segments == null || segments.size() < 2) continue;

            if (needsRoughCheck && segments.size() > 1) {
                BlockPos start = segments.get(0).middlePos();
                BlockPos end = segments.get(segments.size() - 1).middlePos();
                RoadDebugScreen.ScreenPos startScreen = converter.worldToScreen(start.getX(), start.getZ());
                RoadDebugScreen.ScreenPos endScreen = converter.worldToScreen(end.getX(), end.getZ());
                
                if (!bounds.isInBounds(startScreen.x(), startScreen.y(), 200) && 
                    !bounds.isInBounds(endScreen.x(), endScreen.y(), 200)) {
                    continue;
                }
            }

            drawRoadPathWithLOD(ctx, segments, roadColor, roadLOD, converter);
        }
    }
    
    private void drawRoadPathWithLOD(GuiGraphics ctx, List<Records.RoadSegmentPlacement> segments, 
                                     int color, RoadLODLevel roadLOD, WorldToScreenConverter converter) {
        int step = switch (roadLOD) {
            case FINEST -> 1;
            case THIRTY_SECOND -> 32;
            case SIXTY_FOURTH -> 64;
            case ONE_TWENTY_EIGHTH -> 128;
            case TWO_FIFTY_SIXTH -> 256;
            case FIVE_TWELVE -> 512;
            case ONE_THOUSAND_TWENTY_FOURTH -> 1024;
            case NONE -> Integer.MAX_VALUE;
        };
        
        if (step >= segments.size()) return;
        
        RoadDebugScreen.ScreenPos prevPos = null;
        int drawnSegments = 0;
        int maxSegments = 10000;
        
        for (int i = 0; i < segments.size() && drawnSegments < maxSegments; i += step) {
            BlockPos pos = segments.get(i).middlePos();
            RoadDebugScreen.ScreenPos currentPos = converter.worldToScreen(pos.getX(), pos.getZ());
            
            if (!bounds.isInBounds(currentPos.x(), currentPos.y(), 100)) {
                prevPos = currentPos;
                continue;
            }
            
            if (prevPos != null && i > 0) {
                if (bounds.isLineInBounds(prevPos.x(), prevPos.y(), currentPos.x(), currentPos.y())) {
                    RenderUtils.drawLine(ctx, prevPos.x(), prevPos.y(), currentPos.x(), currentPos.y(), color);
                    drawnSegments++;
                }
            }
            prevPos = currentPos;
        }
    }
    
    /**
     * 绘制连接线 - 修复：优先使用道路数据，回退到直线
     */
    public void drawConnections(GuiGraphics ctx, List<Records.StructureConnection> connections,
                               List<Records.RoadData> roads, LODLevel lod, 
                               WorldToScreenConverter converter) {
        if (connections == null || connections.isEmpty()) return;
        if (lod == LODLevel.MINIMAL) return;
        
        for (Records.StructureConnection connection : connections) {
            // 只绘制非完成状态的连接
            if (connection.status() == Records.ConnectionStatus.COMPLETED) {
                continue;
            }
            
            // 尝试找到对应的道路数据
            Records.RoadData matchingRoad = findMatchingRoad(connection, roads);
            
            if (matchingRoad != null && matchingRoad.roadSegmentList() != null 
                && matchingRoad.roadSegmentList().size() >= 2) {
                // 使用道路数据绘制实际路径（简化版，只绘制起点到终点的连接）
                List<Records.RoadSegmentPlacement> segments = matchingRoad.roadSegmentList();
                BlockPos start = segments.get(0).middlePos();
                BlockPos end = segments.get(segments.size() - 1).middlePos();
                
                RoadDebugScreen.ScreenPos fromPos = converter.worldToScreen(start.getX(), start.getZ());
                RoadDebugScreen.ScreenPos toPos = converter.worldToScreen(end.getX(), end.getZ());
                
                if (bounds.isLineInBounds(fromPos.x(), fromPos.y(), toPos.x(), toPos.y())) {
                    int color = getConnectionColor(connection);
                    RenderUtils.drawDashedLine(ctx, fromPos.x(), fromPos.y(), toPos.x(), toPos.y(), color);
                }
            } else {
                // 回退到直线连接
                RoadDebugScreen.ScreenPos fromPos = converter.worldToScreen(
                    connection.from().getX(), connection.from().getZ());
                RoadDebugScreen.ScreenPos toPos = converter.worldToScreen(
                    connection.to().getX(), connection.to().getZ());
                
                if (bounds.isLineInBounds(fromPos.x(), fromPos.y(), toPos.x(), toPos.y())) {
                    int color = getConnectionColor(connection);
                    RenderUtils.drawDashedLine(ctx, fromPos.x(), fromPos.y(), toPos.x(), toPos.y(), color);
                }
            }
        }
    }
    
    private Records.RoadData findMatchingRoad(Records.StructureConnection connection, List<Records.RoadData> roads) {
        if (roads == null || roads.isEmpty()) return null;
        
        for (Records.RoadData road : roads) {
            if (road.roadSegmentList() == null || road.roadSegmentList().isEmpty()) continue;
            
            // 检查道路的起点和终点是否匹配连接
            BlockPos roadStart = road.roadSegmentList().get(0).middlePos();
            BlockPos roadEnd = road.roadSegmentList().get(road.roadSegmentList().size() - 1).middlePos();
            
            // 允许一定的误差范围（100格内）
            if (isNearby(roadStart, connection.from(), 100) && isNearby(roadEnd, connection.to(), 100)) {
                return road;
            }
            if (isNearby(roadStart, connection.to(), 100) && isNearby(roadEnd, connection.from(), 100)) {
                return road;
            }
        }
        
        return null;
    }
    
    private boolean isNearby(BlockPos pos1, BlockPos pos2, int maxDistance) {
        int dx = pos1.getX() - pos2.getX();
        int dz = pos1.getZ() - pos2.getZ();
        return dx * dx + dz * dz <= maxDistance * maxDistance;
    }
    
    private int getConnectionColor(Records.StructureConnection connection) {
        return switch (connection.status()) {
            case PLANNED -> statusColors.get("planned");
            case GENERATING -> statusColors.get("generating");
            case FAILED -> statusColors.get("failed");
            default -> statusColors.get("completed");
        };
    }
    
    /**
     * 绘制结构节点
     */
    public void drawStructures(GuiGraphics ctx, List<BlockPos> structures, 
                              BlockPos hoveredStructure, LODLevel lod,
                              WorldToScreenConverter converter) {
        if (structures == null || structures.isEmpty()) return;
        
        int adaptiveRadius = getAdaptiveNodeRadius(lod, 3.0); // 假设zoom=3.0，实际应传入
        
        for (BlockPos structure : structures) {
            RoadDebugScreen.ScreenPos pos = converter.worldToScreen(structure.getX(), structure.getZ());
            
            if (!bounds.isInBounds(pos.x(), pos.y(), adaptiveRadius + 6)) {
                continue;
            }
            
            boolean isHovered = structure.equals(hoveredStructure);
            int radius = isHovered ? adaptiveRadius + 2 : adaptiveRadius;
            
            switch (lod) {
                case HIGH -> {
                    // 高细节：发光效果
                    int glowColor = 0x402ECC71;
                    RenderUtils.fillCircle(ctx, pos.x(), pos.y(), radius + 1, glowColor);
                    RenderUtils.fillCircle(ctx, pos.x(), pos.y(), radius, statusColors.get("structure"));
                    RenderUtils.drawCircleOutline(ctx, pos.x(), pos.y(), radius, 0xFF1E7E34);
                    
                    // 高光
                    int highlightSize = Math.max(1, radius / 3);
                    ctx.fill(pos.x() - highlightSize, pos.y() - highlightSize, 
                            pos.x() + highlightSize + 1, pos.y() + highlightSize + 1, 0x80FFFFFF);
                }
                case MEDIUM -> {
                    RenderUtils.fillCircle(ctx, pos.x(), pos.y(), radius, statusColors.get("structure"));
                    RenderUtils.drawCircleOutline(ctx, pos.x(), pos.y(), radius, 0xFF1E7E34);
                    if (radius >= 3) {
                        ctx.fill(pos.x() - 1, pos.y() - 1, pos.x() + 1, pos.y() + 1, 0x60FFFFFF);
                    }
                }
                case LOW -> {
                    RenderUtils.fillCircle(ctx, pos.x(), pos.y(), radius, statusColors.get("structure"));
                    if (radius >= 4) {
                        RenderUtils.drawCircleOutline(ctx, pos.x(), pos.y(), radius, 0xFF1E7E34);
                    }
                }
                case MINIMAL -> {
                    if (adaptiveRadius >= 2) {
                        ctx.fill(pos.x() - 1, pos.y() - 1, pos.x() + 2, pos.y() + 2, statusColors.get("structure"));
                    } else {
                        RenderUtils.fillCircle(ctx, pos.x(), pos.y(), adaptiveRadius, statusColors.get("structure"));
                    }
                }
            }
        }
    }
    
    /**
     * 绘制玩家标记
     */
    public void drawPlayerMarker(GuiGraphics ctx, LODLevel lod, double zoom,
                                WorldToScreenConverter converter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        RoadDebugScreen.ScreenPos p = converter.worldToScreen(px, pz);
        
        int playerRadius = Math.max(3, getAdaptiveNodeRadius(lod, zoom) + 1);
        
        if (!bounds.isInBounds(p.x(), p.y(), playerRadius + 6)) return;

        final int fill = 0xFFE74C3C;
        final int glow = 0x40E74C3C;
        final int outline = 0xFF932D1F;

        switch (lod) {
            case HIGH -> {
                RenderUtils.fillCircle(ctx, p.x(), p.y(), playerRadius + 1, glow);
                RenderUtils.fillCircle(ctx, p.x(), p.y(), playerRadius, fill);
                RenderUtils.drawCircleOutline(ctx, p.x(), p.y(), playerRadius, outline);
                int highlightSize = Math.max(1, playerRadius / 4);
                ctx.fill(p.x() - highlightSize, p.y() - highlightSize, 
                        p.x() + highlightSize + 1, p.y() + highlightSize + 1, 0xAAFFFFFF);
                
                float yaw = mc.player.getYRot();
                double angle = Math.toRadians(yaw) + Math.PI / 2.0;
                int arrowLength = playerRadius + 3;
                int tx = p.x() + (int) Math.round(Math.cos(angle) * arrowLength);
                int ty = p.y() + (int) Math.round(Math.sin(angle) * arrowLength);
                RenderUtils.drawLine(ctx, p.x(), p.y(), tx, ty, 0xFFFFFFFF);
            }
            case MEDIUM -> {
                RenderUtils.fillCircle(ctx, p.x(), p.y(), playerRadius, fill);
                RenderUtils.drawCircleOutline(ctx, p.x(), p.y(), playerRadius, outline);
                ctx.fill(p.x() - 1, p.y() - 1, p.x() + 1, p.y() + 1, 0x88FFFFFF);
            }
            case LOW -> {
                RenderUtils.fillCircle(ctx, p.x(), p.y(), playerRadius, fill);
                if (playerRadius >= 4) {
                    RenderUtils.drawCircleOutline(ctx, p.x(), p.y(), playerRadius, outline);
                }
            }
            case MINIMAL -> {
                RenderUtils.fillCircle(ctx, p.x(), p.y(), Math.max(2, playerRadius), fill);
            }
        }
    }
    
    // 计算自适应节点大小
    private int getAdaptiveNodeRadius(LODLevel lod, double zoom) {
        double baseRadius = RADIUS;
        double zoomFactor = Math.max(0.3, Math.min(1.2, 1.0 + Math.log10(zoom) * 0.15));
        double scaledRadius = baseRadius * zoomFactor;
        
        double lodMultiplier = switch (lod) {
            case HIGH -> 0.9;
            case MEDIUM -> 1.0;
            case LOW -> 0.8;
            case MINIMAL -> 0.6;
        };
        
        return Math.max(2, (int) Math.round(scaledRadius * lodMultiplier));
    }
    
    /**
     * 世界坐标到屏幕坐标转换器接口
     */
    public interface WorldToScreenConverter {
        RoadDebugScreen.ScreenPos worldToScreen(double worldX, double worldZ);
    }
}

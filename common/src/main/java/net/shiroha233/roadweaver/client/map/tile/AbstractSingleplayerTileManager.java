package net.shiroha233.roadweaver.client.map.tile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.map.tile.core.MapTileAoi;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.storage.ServerMapTileStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * 单人模式低精度瓦片调度器。
 * 只负责读取已生成的瓦片，不触发任何生成操作。
 */
public abstract class AbstractSingleplayerTileManager {
    private ServerLevel level;
    private MapTileRect visibleRect;
    private int zoom;
    private boolean lastRenderHadTexture;

    public synchronized void request(ServerLevel level, MapTileAoi aoi, MapView view, int contentW, int contentH) {
        if (level == null || view == null || contentW <= 0 || contentH <= 0) {
            return;
        }
        int nextZoom = chooseZoom(view, contentW, contentH);
        MapTileRect viewRect = MapTileScheme.tileRectForBlockRect(
                nextZoom,
                (int) Math.floor(view.getMinX()),
                (int) Math.floor(view.getMinZ()),
                (int) Math.ceil(view.getMaxX()),
                (int) Math.ceil(view.getMaxZ()));
        this.level = level;
        this.visibleRect = viewRect;
        this.zoom = nextZoom;
    }

    public void render(GuiGraphicsExtractor g,
                       Minecraft mc,
                       MapView view,
                       int mapX,
                       int mapY,
                       int contentW,
                       int contentH) {
        if (mc == null || view == null || level == null || visibleRect == null) {
            lastRenderHadTexture = false;
            return;
        }
        lastRenderHadTexture = false;
        Set<String> viewportKeys = new HashSet<>();

        for (MapTileCoord coord : visibleRect.coords()) {
            Path path = ServerMapTileStorage.path(level, layer(), coord);
            if (path == null || !Files.exists(path)) continue;

            String key = path.toAbsolutePath().normalize().toString();
            viewportKeys.add(key);

            Identifier texture = ClientMapTileTextureCache.getOrLoad(mc, path, true);
            if (texture == null) continue;

            lastRenderHadTexture = true;
            blitTile(g, texture, coord, view, mapX, mapY, contentW, contentH);
        }

        ClientMapTileTextureCache.trimToViewport(mc, viewportKeys);
    }

    public int zoom() {
        return zoom;
    }

    public MapTileRect visibleRect() {
        return visibleRect;
    }

    public boolean hasRenderableTiles() {
        return lastRenderHadTexture;
    }

    public synchronized void clear() {
        this.level = null;
        this.visibleRect = null;
        this.zoom = 0;
        this.lastRenderHadTexture = false;
    }

    protected abstract MapTileLayer layer();

    private static void blitTile(GuiGraphicsExtractor g, Identifier texture, MapTileCoord coord,
                                  MapView view, int mapX, int mapY, int contentW, int contentH) {
        int worldMinX = MapTileScheme.tileMinBlockX(coord);
        int worldMinZ = MapTileScheme.tileMinBlockZ(coord);
        int worldMaxX = worldMinX + MapTileScheme.blocksPerTile(coord.zoom());
        int worldMaxZ = worldMinZ + MapTileScheme.blocksPerTile(coord.zoom());
        int x0 = view.toScreenX(worldMinX, mapX, 0, contentW);
        int y0 = view.toScreenY(worldMinZ, mapY, 0, contentH);
        int x1 = view.toScreenX(worldMaxX, mapX, 0, contentW);
        int y1 = view.toScreenY(worldMaxZ, mapY, 0, contentH);
        int drawX = Math.min(x0, x1);
        int drawY = Math.min(y0, y1);
        int drawW = Math.max(1, Math.abs(x1 - x0));
        int drawH = Math.max(1, Math.abs(y1 - y0));
        // 26.3 的 blit 使用归一化 UV（u0, u1, v0, v1），不再是像素尺寸。
        g.blit(texture, drawX, drawY, drawW, drawH, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    private static int chooseZoom(MapView view, int contentW, int contentH) {
        double ppbX = view.pxPerBlockX(contentW);
        double ppbZ = view.pxPerBlockZ(contentH);
        double ppb = Math.min(ppbX, ppbZ);
        if (ppb <= 0) return MapTileScheme.MIN_ZOOM;
        double blocksPerPixel = 1.0 / ppb;
        return MapTileScheme.zoomForBlocksPerPixel(blocksPerPixel);
    }
}

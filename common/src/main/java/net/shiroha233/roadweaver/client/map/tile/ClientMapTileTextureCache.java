package net.shiroha233.roadweaver.client.map.tile;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 瓦片纹理双层缓存。
 *
 * 性能优化：
 * 1. 增大缓存容量以支持全精度渲染（数百个 chunk 纹片）
 * 2. 异步预加载纹理，避免主线程阻塞
 * 3. 视口缓存命中时检查 lastModified（规划可能更新PNG）
 */
public final class ClientMapTileTextureCache {
    private ClientMapTileTextureCache() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private static final int MAX_VIEWPORT_TEXTURES = 256;
    private static final int MAX_BACKGROUND_TEXTURES = 512;

    private static final LinkedHashMap<String, TextureEntry> VIEWPORT_CACHE = new LinkedHashMap<>(512, 0.75f, true);
    private static final LinkedHashMap<String, TextureEntry> BACKGROUND_CACHE = new LinkedHashMap<>(1024, 0.75f, true);

    private static final ExecutorService LOAD_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "RW-TileLoader");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<String, CompletableFuture<Identifier>> PENDING_LOADS = new ConcurrentHashMap<>();

    public static synchronized Identifier getOrLoad(Minecraft mc, Path path, boolean inViewport) {
        if (mc == null || path == null || !Files.exists(path)) {
            return null;
        }
        String key = path.toAbsolutePath().normalize().toString();
        long currentLastMod = lastModified(path);

        TextureEntry vpEntry = VIEWPORT_CACHE.get(key);
        if (vpEntry != null) {
            if (inViewport && vpEntry.lastModified == currentLastMod) {
                return vpEntry.location;
            }
            VIEWPORT_CACHE.remove(key);
            release(mc, vpEntry.location);
        }

        TextureEntry bgEntry = BACKGROUND_CACHE.get(key);
        if (bgEntry != null) {
            if (bgEntry.lastModified == currentLastMod) {
                if (inViewport) {
                    BACKGROUND_CACHE.remove(key);
                    VIEWPORT_CACHE.put(key, bgEntry);
                    trimViewportCache(mc);
                }
                return bgEntry.location;
            }
            BACKGROUND_CACHE.remove(key);
            release(mc, bgEntry.location);
        }

        CompletableFuture<Identifier> pending = PENDING_LOADS.get(key);
        if (pending != null) {
            if (pending.isDone()) {
                PENDING_LOADS.remove(key);
                try {
                    Identifier loc = pending.getNow(null);
                    if (loc != null) return loc;
                } catch (Exception ignored) {}
            }
            return null;
        }

        startAsyncLoad(mc, path, key, currentLastMod, inViewport);
        return null;
    }

    public static synchronized Identifier getOrLoad(Minecraft mc, Path path) {
        return getOrLoad(mc, path, false);
    }

    /**
     * 异步预加载纹理到后台缓存。不阻塞渲染线程。
     * 下次 getOrLoad 时可直接命中缓存。
     */
    public static void preloadAsync(Minecraft mc, Path path) {
        if (mc == null || path == null || !Files.exists(path)) return;
        String key = path.toAbsolutePath().normalize().toString();

        synchronized (ClientMapTileTextureCache.class) {
            if (VIEWPORT_CACHE.containsKey(key) || BACKGROUND_CACHE.containsKey(key) || PENDING_LOADS.containsKey(key)) {
                return;
            }
        }

        startAsyncLoad(mc, path, key, lastModified(path), false);
    }

    public static synchronized void trimToViewport(Minecraft mc, Set<String> viewportKeys) {
        if (mc == null) return;
        Iterator<Map.Entry<String, TextureEntry>> it = BACKGROUND_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TextureEntry> entry = it.next();
            if (!viewportKeys.contains(entry.getKey())) {
                release(mc, entry.getValue().location);
                it.remove();
            }
        }
    }

    public static synchronized void invalidate(Minecraft mc, Path path) {
        if (path == null) return;
        String key = path.toAbsolutePath().normalize().toString();
        TextureEntry vpEntry = VIEWPORT_CACHE.remove(key);
        if (vpEntry != null && mc != null) {
            release(mc, vpEntry.location);
        }
        TextureEntry bgEntry = BACKGROUND_CACHE.remove(key);
        if (bgEntry != null && mc != null) {
            release(mc, bgEntry.location);
        }
    }

    public static synchronized void clear(Minecraft mc) {
        if (mc != null) {
            for (TextureEntry entry : VIEWPORT_CACHE.values()) {
                release(mc, entry.location);
            }
            for (TextureEntry entry : BACKGROUND_CACHE.values()) {
                release(mc, entry.location);
            }
        }
        VIEWPORT_CACHE.clear();
        BACKGROUND_CACHE.clear();
    }

    private static CompletableFuture<Identifier> startAsyncLoad(Minecraft mc,
                                                                      Path path,
                                                                      String key,
                                                                      long lastMod,
                                                                      boolean inViewport) {
        CompletableFuture<Identifier> future = new CompletableFuture<>();
        CompletableFuture<Identifier> existing = PENDING_LOADS.putIfAbsent(key, future);
        if (existing != null) {
            return existing;
        }

        LOAD_EXECUTOR.execute(() -> {
            NativeImage image = null;
            try (InputStream input = Files.newInputStream(path)) {
                image = NativeImage.read(input);
                NativeImage loadedImage = image;
                image = null;
                mc.execute(() -> registerLoadedTexture(mc, path, key, lastMod, inViewport, loadedImage, future));
            } catch (IOException e) {
                deleteCorruptTile(path, e);
                future.complete(null);
            } catch (Throwable t) {
                if (image != null) {
                    image.close();
                }
                LOGGER.warn("地图瓦片 PNG 加载失败: {}", path, t);
                future.complete(null);
            }
        });
        future.whenComplete((loc, ex) -> PENDING_LOADS.remove(key, future));
        return future;
    }

    private static void registerLoadedTexture(Minecraft mc,
                                              Path path,
                                              String key,
                                              long lastMod,
                                              boolean inViewport,
                                              NativeImage image,
                                              CompletableFuture<Identifier> future) {
        DynamicTexture texture = null;
        try {
            texture = new DynamicTexture(() -> "roadweaver_map_tile", image);
            Identifier location = Identifier.fromNamespaceAndPath("roadweaver",
                    "map_tile/" + Integer.toHexString(key.hashCode()));
            mc.getTextureManager().register(location, texture);
            TextureEntry entry = new TextureEntry(location, lastMod);

            synchronized (ClientMapTileTextureCache.class) {
                if (inViewport) {
                    VIEWPORT_CACHE.put(key, entry);
                    trimViewportCache(mc);
                } else {
                    BACKGROUND_CACHE.put(key, entry);
                    trimBackgroundCache(mc);
                }
            }
            future.complete(location);
        } catch (Throwable t) {
            if (texture != null) {
                texture.close();
            } else if (image != null) {
                image.close();
            }
            LOGGER.warn("地图瓦片 PNG 纹理注册失败: {}", path, t);
            future.complete(null);
        }
    }

    private static void trimViewportCache(Minecraft mc) {
        while (VIEWPORT_CACHE.size() > MAX_VIEWPORT_TEXTURES) {
            Iterator<Map.Entry<String, TextureEntry>> it = VIEWPORT_CACHE.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<String, TextureEntry> eldest = it.next();
                BACKGROUND_CACHE.put(eldest.getKey(), eldest.getValue());
                it.remove();
                trimBackgroundCache(mc);
            }
        }
    }

    private static void trimBackgroundCache(Minecraft mc) {
        while (BACKGROUND_CACHE.size() > MAX_BACKGROUND_TEXTURES) {
            Iterator<Map.Entry<String, TextureEntry>> it = BACKGROUND_CACHE.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<String, TextureEntry> eldest = it.next();
                release(mc, eldest.getValue().location);
                it.remove();
            }
        }
    }

    private static void release(Minecraft mc, Identifier location) {
        if (mc == null || location == null) return;
        mc.getTextureManager().release(location);
    }

    private static void deleteCorruptTile(Path path, IOException cause) {
        try {
            Files.deleteIfExists(path);
            LOGGER.warn("地图瓦片 PNG 损坏，已删除并等待重建: {} ({})", path, cause.getMessage());
        } catch (IOException deleteFailed) {
            LOGGER.warn("地图瓦片 PNG 损坏且删除失败: {}", path, deleteFailed);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private record TextureEntry(Identifier location, long lastModified) {}
}

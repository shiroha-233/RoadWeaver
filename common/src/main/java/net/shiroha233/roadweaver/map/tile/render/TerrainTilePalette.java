package net.shiroha233.roadweaver.map.tile.render;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 低精度瓦片调色板。处于逐采样点调用路径，{@link #colorFor} 内不允许任何分配，
 * 因此生物群系到颜色分类的映射统一缓存。
 */
public final class TerrainTilePalette {
    private TerrainTilePalette() {}

    private static final int CAT_SNOW = 0;
    private static final int CAT_BADLANDS = 1;
    private static final int CAT_DESERT = 2;
    private static final int CAT_SAVANNA = 3;
    private static final int CAT_SWAMP = 4;
    private static final int CAT_JUNGLE = 5;
    private static final int CAT_TAIGA = 6;
    /** 未匹配到生物群系专属色，交由高度分支决定。 */
    private static final int CAT_BY_HEIGHT = -1;

    private static final int[] BIOME_BASE_COLORS = {
            0xFFE9EEF2, // snow / frozen / ice
            0xFFC6784A, // badlands
            0xFFD5C089, // desert / beach
            0xFFB9AE64, // savanna
            0xFF516D42, // swamp / mangrove
            0xFF3A7643, // jungle
            0xFF5B775F  // taiga / spruce
    };

    /** 写时复制的不可变快照，使逐采样点读取无锁。 */
    private static volatile Map<Holder<Biome>, Integer> BIOME_CATEGORY_CACHE = Map.of();
    private static final Object BIOME_CATEGORY_MUTEX = new Object();

    public static int colorFor(Holder<Biome> biome,
                               int height,
                               int seaLevel,
                               int oceanFloor,
                               boolean columnWater,
                               boolean nearWater,
                               double heightShade) {
        if (columnWater) {
            return waterColor(seaLevel, oceanFloor, nearWater);
        }
        int base = landBaseColor(biomeCategory(biome), height, seaLevel, nearWater);
        return HeightShader.multiplyRgb(base, heightShade);
    }

    public static int colorFor(Holder<Biome> biome,
                               int height,
                               int seaLevel,
                               int oceanFloor,
                               boolean columnWater,
                               boolean nearWater) {
        return colorFor(biome, height, seaLevel, oceanFloor, columnWater, nearWater, 1.0);
    }

    private static int waterColor(int seaLevel, int oceanFloor, boolean nearWater) {
        int depth = Math.max(0, seaLevel - oceanFloor);
        double t = clamp(depth / 32.0, 0.0, 1.0);
        int shallow = nearWater ? 0xFF4F82CB : 0xFF4676C0;
        int deep = 0xFF244D97;
        return mix(shallow, deep, t);
    }

    private static int landBaseColor(int biomeCategory, int height, int seaLevel, boolean nearWater) {
        int base;
        if (biomeCategory >= 0) {
            base = BIOME_BASE_COLORS[biomeCategory];
        } else if (height >= seaLevel + 90) {
            base = 0xFF7B7A76;
        } else if (height >= seaLevel + 60) {
            base = 0xFF749060;
        } else {
            base = 0xFF6B9B52;
        }
        if (nearWater) {
            base = mix(base, 0xFFB0A06C, 0.14);
        }
        return base;
    }

    /** 返回该生物群系的颜色分类。 */
    private static int biomeCategory(Holder<Biome> biome) {
        if (biome == null) return CAT_BY_HEIGHT;
        Integer cached = BIOME_CATEGORY_CACHE.get(biome);
        if (cached != null) return cached;
        return cacheAndResolve(biome);
    }

    private static int cacheAndResolve(Holder<Biome> biome) {
        int resolved = resolveCategory(biome);
        synchronized (BIOME_CATEGORY_MUTEX) {
            IdentityHashMap<Holder<Biome>, Integer> next = new IdentityHashMap<>();
            next.putAll(BIOME_CATEGORY_CACHE);
            next.put(biome, resolved);
            // 用 unmodifiableMap 包装而非 Map.copyOf，后者会丢失身份比较语义
            BIOME_CATEGORY_CACHE = Collections.unmodifiableMap(next);
        }
        return resolved;
    }

    /** 冷路径：仅在某生物群系首次出现时执行。 */
    private static int resolveCategory(Holder<Biome> biome) {
        String biomePath = biomePath(biome);
        if (biomePath.isEmpty()) return CAT_BY_HEIGHT;
        if (biomePath.contains("snow") || biomePath.contains("frozen") || biomePath.contains("ice")) {
            return CAT_SNOW;
        }
        if (biomePath.contains("badlands")) {
            return CAT_BADLANDS;
        }
        if (biomePath.contains("desert") || biomePath.contains("beach")) {
            return CAT_DESERT;
        }
        if (biomePath.contains("savanna")) {
            return CAT_SAVANNA;
        }
        if (biomePath.contains("swamp") || biomePath.contains("mangrove")) {
            return CAT_SWAMP;
        }
        if (biomePath.contains("jungle")) {
            return CAT_JUNGLE;
        }
        if (biomePath.contains("taiga") || biomePath.contains("spruce")) {
            return CAT_TAIGA;
        }
        return CAT_BY_HEIGHT;
    }

    private static String biomePath(Holder<Biome> biome) {
        if (biome == null) return "";
        return biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
    }

    private static int mix(int from, int to, double t) {
        double clamped = clamp(t, 0.0, 1.0);
        int a0 = (from >>> 24) & 0xFF;
        int r0 = (from >>> 16) & 0xFF;
        int g0 = (from >>> 8) & 0xFF;
        int b0 = from & 0xFF;
        int a1 = (to >>> 24) & 0xFF;
        int r1 = (to >>> 16) & 0xFF;
        int g1 = (to >>> 8) & 0xFF;
        int b1 = to & 0xFF;
        int a = HeightShader.clampChannel((int) Math.round(a0 + (a1 - a0) * clamped));
        int r = HeightShader.clampChannel((int) Math.round(r0 + (r1 - r0) * clamped));
        int g = HeightShader.clampChannel((int) Math.round(g0 + (g1 - g0) * clamped));
        int b = HeightShader.clampChannel((int) Math.round(b0 + (b1 - b0) * clamped));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端自动规划采样范围缓存。
 */
public final class MapAutomaticPlanningSamplingCache {
    private static final ConcurrentHashMap<ResourceLocation, List<AutomaticPlanningSamplingBounds>> BY_DIMENSION =
            new ConcurrentHashMap<>();

    private MapAutomaticPlanningSamplingCache() {}

    public static void replace(ResourceLocation dimension,
                               List<AutomaticPlanningSamplingBounds> bounds) {
        if (dimension == null) {
            return;
        }
        List<AutomaticPlanningSamplingBounds> snapshot = immutableBounds(bounds);
        if (snapshot.isEmpty()) {
            BY_DIMENSION.remove(dimension);
        } else {
            BY_DIMENSION.put(dimension, snapshot);
        }
    }

    public static List<AutomaticPlanningSamplingBounds> snapshot(ResourceLocation dimension) {
        if (dimension == null) {
            return List.of();
        }
        return BY_DIMENSION.getOrDefault(dimension, List.of());
    }

    public static void clear() {
        BY_DIMENSION.clear();
    }

    private static List<AutomaticPlanningSamplingBounds> immutableBounds(
            List<AutomaticPlanningSamplingBounds> bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return List.of();
        }
        ArrayList<AutomaticPlanningSamplingBounds> copy = new ArrayList<>(bounds.size());
        for (AutomaticPlanningSamplingBounds bound : bounds) {
            if (bound != null) {
                copy.add(bound);
            }
        }
        return copy.isEmpty() ? List.of() : List.copyOf(copy);
    }
}

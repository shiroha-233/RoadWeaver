package net.shiroha233.roadweaver.terrain.accurate;

import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/**
 * 精确高度样本存储装配器：未注册时回退为纯内存缓存。
 */
public final class AccurateSampleStoreProviders {
    private static final AccurateSampleStoreProvider NOOP_PROVIDER = level -> AccurateSampleStore.noop();
    private static volatile AccurateSampleStoreProvider provider = NOOP_PROVIDER;

    private AccurateSampleStoreProviders() {
    }

    public static void install(AccurateSampleStoreProvider next) {
        provider = next == null ? NOOP_PROVIDER : next;
    }

    public static AccurateSampleStore create(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return provider.create(level);
    }
}

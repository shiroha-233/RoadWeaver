package net.shiroha233.roadweaver.terrain.accurate;

import net.minecraft.server.level.ServerLevel;

/**
 * 精确高度样本存储装配端口，由持久层在启动期实现并注册。
 */
@FunctionalInterface
public interface AccurateSampleStoreProvider {
    AccurateSampleStore create(ServerLevel level);
}

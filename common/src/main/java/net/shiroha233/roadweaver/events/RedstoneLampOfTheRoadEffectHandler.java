package net.shiroha233.roadweaver.events;

import dev.architectury.event.events.common.TickEvent;
import net.shiroha233.roadweaver.registry.ModBlocks;
import net.shiroha233.roadweaver.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedstoneLampOfTheRoadEffectHandler {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public static void register() {
        TickEvent.SERVER_POST.register(server -> {
            if (server.getTickCount() % 10 != 0) return;

            // 收集所有玩家信息，准备传给异步线程
            List<PlayerCheckData> playerDataList = new ArrayList<>();
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    playerDataList.add(new PlayerCheckData(player, level, player.blockPosition()));
                }
            }

            // 异步线程处理检测附近方块
            EXECUTOR.submit(() -> {
                List<PlayerCheckData> playersNearBlock = new ArrayList<>();

                for (PlayerCheckData data : playerDataList) {
                    boolean nearblock = false;
                    int radius = 5;

                    for (int dx = -radius; dx <= radius && !nearblock; dx++) {
                        for (int dy = -radius; dy <= radius && !nearblock; dy++) {
                            for (int dz = -radius; dz <= radius && !nearblock; dz++) {
                                BlockPos checkPos = data.pos.offset(dx, dy, dz);
                                BlockState state = data.level.getBlockState(checkPos);
                                if (state.is(ModBlocks.REDSTONE_LAMP_OF_THE_ROAD.get())) {
                                    nearblock = true;
                                }
                            }
                        }
                    }

                    if (nearblock) {
                        playersNearBlock.add(data);
                    }
                }

                // 主线程给玩家加效果
                server.execute(() -> {
                    for (PlayerCheckData data : playersNearBlock) {
                        data.player.addEffect(new MobEffectInstance(ModEffects.POWER_OF_THE_ROAD.get(), 150, 0, true, true));

                    }
                });
            });
        });
    }

    private static class PlayerCheckData {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos pos;

        PlayerCheckData(ServerPlayer player, ServerLevel level, BlockPos pos) {
            this.player = player;
            this.level = level;
            this.pos = pos;
        }
    }
}

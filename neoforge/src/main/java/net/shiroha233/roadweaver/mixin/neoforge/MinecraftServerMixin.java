/* 文件职责：在 NeoForge 服务端关卡准备阶段触发 RoadWeaver 初始化。 */
package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.storage.ServerLevelData;
import net.shiroha233.roadweaver.generation.InitialSpawnPlanning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"))
    private static void roadweaver$planBeforeSpawnSearch(ServerLevel level, ServerLevelData levelData, boolean bonusChest,
                                                       boolean debug, CallbackInfo ci) {
        InitialSpawnPlanning.beforeSpawnSearch(level, levelData);
    }

    @Inject(method = "setInitialSpawn", at = @At("RETURN"))
    private static void roadweaver$placeCabinAfterSpawnSearch(ServerLevel level, ServerLevelData levelData, boolean bonusChest,
                                                            boolean debug, CallbackInfo ci) {
        InitialSpawnPlanning.afterSpawnSearch(level);
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void roadweaver$preloadBeforePrepareLevels(ChunkProgressListener listener, CallbackInfo ci) {
        InitialSpawnPlanning.beforePrepareLevels((MinecraftServer)(Object)this);
    }
}

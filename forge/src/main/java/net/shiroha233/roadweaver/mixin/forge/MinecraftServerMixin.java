/* 文件职责：在 Forge 服务端关卡准备阶段触发 RoadWeaver 初始化。 */
package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void roadweaver$preloadBeforePrepareLevels(ChunkProgressListener listener, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer)(Object)this;
        ServerLevel level = server.overworld();
        if (level == null) return;
        RoadShardStorage.preload(level);
        if (InitialGenManager.shouldRunInitialGeneration(level)) {
            InitialGenManager.begin(level);
            InitialGenManager.blockUntilDone(level);
        } else {
            RoadGenerationService.onServerStarted();
        }
    }
}

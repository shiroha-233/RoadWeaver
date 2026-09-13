package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.structures.precompute.StructureInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin 钩入区块的 STRUCTURE_STARTS 阶段
 * 
 * 在原版结构生成完成后，注入预计算的路边结构。
 * 这样 Beardifier 在噪声生成阶段可以检测到这些结构并自动进行地形适应。
 * 
 * 1.20.1 适配：钩入 ServerLevel.onStructureStartsAvailable 方法
 */
@Mixin(ServerLevel.class)
public class ServerLevelStructureMixin {
    
    /**
     * 在 onStructureStartsAvailable 方法头部注入路边结构
     */
    @Inject(
        method = "onStructureStartsAvailable",
        at = @At("HEAD")
    )
    private void roadweaver$injectRoadsideStructures(ChunkAccess chunk, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        StructureInjector.injectPendingStructures(level, chunk);
        SignTextService.onChunkReady(level, chunk.getPos());
    }
}

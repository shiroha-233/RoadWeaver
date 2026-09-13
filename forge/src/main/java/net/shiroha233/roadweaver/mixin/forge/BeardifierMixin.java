/* 文件职责：将预编译的区块道路密度 stamp 接入 Forge 原版 Beardifier。 */
package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.shiroha233.roadweaver.beardifier.RoadBeardifierAccess;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.worldgen.road.RoadChunkPlan;
import net.shiroha233.roadweaver.worldgen.road.RoadWorldgenPlanCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Beardifier.class)
public class BeardifierMixin implements RoadBeardifierAccess {

    @Unique
    private static final Logger roadweaver$LOGGER = LoggerFactory.getLogger("RoadWeaver/BeardifierMixin");

    @Unique
    private RoadChunkPlan roadweaver$roadChunkPlan;

    @Override
    public void roadweaver$setRoadChunkPlan(RoadChunkPlan plan) {
        this.roadweaver$roadChunkPlan = plan;
    }

    @Override
    public RoadChunkPlan roadweaver$getRoadChunkPlan() {
        return this.roadweaver$roadChunkPlan;
    }

    @Inject(method = "forStructuresInChunk", at = @At("RETURN"))
    private static void roadweaver$injectRoadData(StructureManager mgr, ChunkPos pos,
                                                   CallbackInfoReturnable<Beardifier> cir) {
        try {
            ModConfig cfg = ConfigService.get();
            if (cfg == null) return;

            LevelAccessor levelAccessor = ((StructureManagerAccessor) mgr).roadweaver$getLevel();
            ServerLevel serverLevel = roadweaver$resolveServerLevel(levelAccessor);
            if (serverLevel == null || !Level.OVERWORLD.equals(serverLevel.dimension())) return;

            RoadChunkPlan plan = RoadWorldgenPlanCache.get(serverLevel, pos, cfg);
            ((RoadBeardifierAccess) cir.getReturnValue()).roadweaver$setRoadChunkPlan(plan);
        } catch (Exception e) {
            roadweaver$LOGGER.debug("Failed to inject road data into Beardifier", e);
        }
    }

    @Inject(method = "compute", at = @At("RETURN"), cancellable = true)
    private void roadweaver$addRoadDensity(DensityFunction.FunctionContext ctx,
                                           CallbackInfoReturnable<Double> cir) {
        RoadChunkPlan plan = roadweaver$roadChunkPlan;
        if (plan == null || plan.densityStamp().isEmpty()) return;

        double road = plan.densityStamp().density(ctx.blockX(), ctx.blockY(), ctx.blockZ());
        if (road != 0.0) {
            cir.setReturnValue(cir.getReturnValue() + road);
        }
    }

    @Unique
    private static ServerLevel roadweaver$resolveServerLevel(LevelAccessor accessor) {
        if (accessor instanceof ServerLevel sl) return sl;
        if (accessor instanceof WorldGenRegion wgr) return wgr.getLevel();
        return null;
    }
}

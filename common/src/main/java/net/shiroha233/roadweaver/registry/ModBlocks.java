package net.shiroha233.roadweaver.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.shiroha233.roadweaver.content.blocks.RedstoneLampOfTheRoad;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
public class ModBlocks {
    public static final String MOD_ID = "roadweaver";
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> REDSTONE_LAMP_OF_THE_ROAD = BLOCKS.register("redstone_lamp_of_the_road",
            () -> new RedstoneLampOfTheRoad(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F, 1.0F)
                    .lightLevel(state -> state.getValue(RedstoneLampOfTheRoad.LIT) ? 15 : 0)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .emissiveRendering((s, w, p) -> s.getValue(RedstoneLampOfTheRoad.LIT))
            )
    );



    public static void register() {
        BLOCKS.register();
    }
}

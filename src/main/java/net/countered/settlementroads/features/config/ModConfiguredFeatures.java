package net.countered.settlementroads.features.config;

import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.features.RoadFeature;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registerable;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class ModConfiguredFeatures {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static void bootstrap(Registerable<ConfiguredFeature<?,?>> context){
        LOGGER.info("Bootstrap ConfiguredFeature");
        context.register(RoadFeature.ROAD_FEATURE_KEY,
                new ConfiguredFeature<>(RoadFeature.ROAD_FEATURE,
                        new RoadFeatureConfig(
                                // artificial
                                List.of(List.of(Blocks.MUD_BRICKS.getDefaultState(), Blocks.PACKED_MUD.getDefaultState()),
                                        List.of(Blocks.POLISHED_ANDESITE.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState()),
                                        List.of(Blocks.STONE_BRICKS.getDefaultState(), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Blocks.CRACKED_STONE_BRICKS.getDefaultState())),
                                // natural
                                List.of(List.of(Blocks.COARSE_DIRT.getDefaultState(), Blocks.ROOTED_DIRT.getDefaultState(), Blocks.PACKED_MUD.getDefaultState()),
                                        List.of(Blocks.COBBLESTONE.getDefaultState(), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.CRACKED_STONE_BRICKS.getDefaultState()),
                                        List.of(Blocks.DIRT_PATH.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState(), Blocks.PACKED_MUD.getDefaultState())),
                                List.of(3),                                                                                                           // width
                                List.of(1,2,3,4,5,6,7,8,9)                                                                                                // quality (not used)
                        )
                )
        );
    }
}
package net.shiroha233.roadweaver.generation;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.RoadAppearanceConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.core.Road;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoadGenerationServiceTest {

    @Test
    void generateTaskFailsWhenRoadGenerationProducesNoRoadData() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        ModConfig modConfig = ConfigService.get();
        RoadAppearanceConfig roadAppearance = modConfig.roadAppearance();
        boolean roadsEnabled = roadAppearance.roadsEnabled();
        boolean allowArtificial = roadAppearance.allowArtificial();
        boolean allowNatural = roadAppearance.allowNatural();

        try {
            roadAppearance.setRoadsEnabled(true);
            roadAppearance.setAllowArtificial(false);
            roadAppearance.setAllowNatural(false);

            ServerLevel level = mock(ServerLevel.class);
            RegistryAccess registryAccess = mock(RegistryAccess.class);
            @SuppressWarnings("unchecked")
            Registry<ConfiguredFeature<?, ?>> configuredFeatures = mock(Registry.class);

            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.registryAccess()).thenReturn(registryAccess);
            when(registryAccess.registryOrThrow(Registries.CONFIGURED_FEATURE)).thenReturn(configuredFeatures);

            StructureConnection connection = new StructureConnection(
                    BlockPos.ZERO,
                    new BlockPos(16, 0, 0)
            );

            Road road = new Road(
                    level,
                    connection,
                    new PathFeatureConfig(),
                    RoadGenerationConfig.from(modConfig)
            );
            assertNull(
                    road.generateRoad(modConfig.pathfindingCost().aStarMaxSteps()),
                    "Test setup must make Road.generateRoad() return null"
            );

            assertFalse(RoadGenerationService.generateTask(level, connection));
        } finally {
            roadAppearance.setRoadsEnabled(roadsEnabled);
            roadAppearance.setAllowArtificial(allowArtificial);
            roadAppearance.setAllowNatural(allowNatural);
        }
    }
}

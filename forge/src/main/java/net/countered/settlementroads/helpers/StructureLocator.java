package net.countered.settlementroads.helpers;

import com.mojang.datafixers.util.Pair;
import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper.StructureLocationsData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class StructureLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static void locateConfiguredStructure(ServerLevel serverWorld, int locateCount, boolean locateAtPlayer) {
        LOGGER.debug("Locating " + locateCount + " " + ModConfig.structureToLocate);
        HolderSet<Structure> targets = resolveTargets(serverWorld);
        if (targets == null) {
            return;
        }

        for (int x = 0; x < locateCount; x++) {
            if (locateAtPlayer) {
                for (ServerPlayer player : serverWorld.players()) {
                    executeLocateStructure(player.blockPosition(), serverWorld, targets);
                }
            } else {
                executeLocateStructure(serverWorld.getSharedSpawnPos(), serverWorld, targets);
            }
        }
    }

    private static void executeLocateStructure(BlockPos locatePos, ServerLevel serverWorld, HolderSet<Structure> targets) {
        Pair<BlockPos, Holder<Structure>> pair = serverWorld.getChunkSource()
                .getGenerator()
                .findNearestMapStructure(serverWorld, targets, locatePos, ModConfig.structureSearchRadius, true);
        if (pair == null) {
            LOGGER.debug("No structure found for {} near {}", ModConfig.structureToLocate, locatePos);
            return;
        }

        BlockPos structureLocation = pair.getFirst();
        LOGGER.debug("Structure found at {}", structureLocation);
        StructureLocationsData data = WorldDataHelper.structureLocations(serverWorld);
        data.add(structureLocation);
    }

    private static HolderSet<Structure> resolveTargets(ServerLevel level) {
        String id = ModConfig.structureToLocate.trim();
        if (id.isEmpty()) {
            LOGGER.warn("Structure selector in config is empty");
            return null;
        }

        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        if (id.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
            if (tagId == null) {
                LOGGER.warn("Invalid structure tag identifier: {}", id);
                return null;
            }
            var tagKey = net.minecraft.tags.TagKey.create(Registries.STRUCTURE, tagId);
            Optional<HolderSet.Named<Structure>> holders = registry.getTag(tagKey);
            if (holders.isEmpty()) {
                LOGGER.warn("Structure tag {} not found", tagId);
                return null;
            }
            return holders.get();
        }

        ResourceLocation keyId = ResourceLocation.tryParse(id);
        if (keyId == null) {
            LOGGER.warn("Invalid structure identifier: {}", id);
            return null;
        }
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, keyId);
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(structureKey);
        if (holder.isEmpty()) {
            LOGGER.warn("Structure {} not found", keyId);
            return null;
        }
        return HolderSet.direct(holder.get());
    }
}

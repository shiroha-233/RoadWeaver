package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 路边村庄拼图规划器
 */
public final class RoadsideVillageJigsawPlanner {
    private RoadsideVillageJigsawPlanner() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/RoadsideVillageJigsawPlanner");
    private static final int ROTATION_ATTEMPTS = 4;

    public static List<PoolElementStructurePiece> createPieces(ServerLevel level,
                                                               PendingRoadsideVillage village,
                                                               StructureTemplateManager templateManager) {
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        RandomSource random = RandomSource.create(village.seed());
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        List<BoundingBox> occupied = new ArrayList<>();

        for (PendingRoadsideVillageSlot slot : village.slots()) {
            Optional<PoolElementStructurePiece> piece = createPieceForSlot(pools, templateManager, slot, village.style(), random, occupied);
            piece.ifPresent(value -> {
                pieces.add(value);
                occupied.add(value.getBoundingBox());
            });
        }

        LOGGER.debug("Created {} roadside village pieces for {}", pieces.size(), village.placementId());
        return pieces;
    }

    private static Optional<PoolElementStructurePiece> createPieceForSlot(Registry<StructureTemplatePool> pools,
                                                                         StructureTemplateManager templateManager,
                                                                         PendingRoadsideVillageSlot slot,
                                                                         ResourceLocation style,
                                                                         RandomSource random,
                                                                         List<BoundingBox> occupied) {
        ResourceLocation poolId = slot.poolId(style);
        ResourceKey<StructureTemplatePool> poolKey = ResourceKey.create(Registries.TEMPLATE_POOL, poolId);
        Optional<Holder.Reference<StructureTemplatePool>> holder = pools.getHolder(poolKey);
        if (holder.isEmpty() || holder.get().value().size() == 0) {
            LOGGER.debug("Roadside village pool {} is not available", poolId);
            return Optional.empty();
        }

        List<StructurePoolElement> candidates = holder.get().value().getShuffledTemplates(random);
        for (StructurePoolElement element : candidates) {
            Optional<BoundingBox> templateBounds = safeBoundingBox(templateManager, element, BlockPos.ZERO, Rotation.NONE);
            if (templateBounds.isEmpty() || shouldSkipElement(slot, templateBounds.get())) {
                continue;
            }

            Optional<PoolElementStructurePiece> piece = orientElement(templateManager, slot, element, random, occupied);
            if (piece.isPresent()) {
                return piece;
            }
        }

        return Optional.empty();
    }

    private static boolean isEmptyElementBounds(BoundingBox box) {
        return box.getXSpan() <= 1 && box.getZSpan() <= 1;
    }

    private static boolean shouldSkipElement(PendingRoadsideVillageSlot slot, BoundingBox box) {
        return slot.kind() != PendingRoadsideVillageSlot.SlotKind.VILLAGER && isEmptyElementBounds(box);
    }

    private static Optional<BoundingBox> safeBoundingBox(StructureTemplateManager templateManager,
                                                         StructurePoolElement element,
                                                         BlockPos position,
                                                         Rotation rotation) {
        try {
            return Optional.of(element.getBoundingBox(templateManager, position, rotation));
        } catch (IllegalStateException ex) {
            LOGGER.debug("Skipping non-placeable roadside village pool element: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static List<StructureTemplate.StructureBlockInfo> safeJigsawBlocks(StructureTemplateManager templateManager,
                                                                               StructurePoolElement element,
                                                                               Rotation rotation,
                                                                               RandomSource random) {
        try {
            return element.getShuffledJigsawBlocks(templateManager, BlockPos.ZERO, rotation, random);
        } catch (IllegalStateException ex) {
            LOGGER.debug("Skipping roadside village pool element without jigsaw data: {}", ex.getMessage());
            return List.of();
        }
    }

    private static Optional<PoolElementStructurePiece> orientElement(StructureTemplateManager templateManager,
                                                                    PendingRoadsideVillageSlot slot,
                                                                    StructurePoolElement element,
                                                                    RandomSource random,
                                                                    List<BoundingBox> occupied) {
        Rotation preferred = rotationForOutward(slot.outward());
        Rotation[] rotations = orderedRotations(preferred);

        for (int i = 0; i < ROTATION_ATTEMPTS; i++) {
            Rotation rotation = rotations[i % rotations.length];
            List<StructureTemplate.StructureBlockInfo> jigsaws = safeJigsawBlocks(templateManager, element, rotation, random);
            for (StructureTemplate.StructureBlockInfo jigsaw : jigsaws) {
                Direction front = JigsawBlock.getFrontFacing(jigsaw.state());
                if (front != slot.outward().getOpposite()) {
                    continue;
                }

                BlockPos position = slot.anchor().subtract(jigsaw.pos());
                Optional<BoundingBox> maybeBox = safeBoundingBox(templateManager, element, position, rotation);
                if (maybeBox.isEmpty()) {
                    continue;
                }
                BoundingBox box = maybeBox.get();
                if (collides(box, occupied)) {
                    continue;
                }

                return Optional.of(new PoolElementStructurePiece(
                    templateManager,
                    element,
                    position,
                    element.getGroundLevelDelta(),
                    rotation,
                    box
                ));
            }
        }

        for (Rotation rotation : rotations) {
            Optional<BoundingBox> templateBounds = safeBoundingBox(templateManager, element, BlockPos.ZERO, rotation);
            if (templateBounds.isEmpty()) {
                continue;
            }

            BoundingBox zeroBox = templateBounds.get();
            BlockPos position = new BlockPos(
                slot.anchor().getX() - (zeroBox.minX() + zeroBox.maxX()) / 2,
                slot.anchor().getY() - zeroBox.minY(),
                slot.anchor().getZ() - (zeroBox.minZ() + zeroBox.maxZ()) / 2
            );
            Optional<BoundingBox> maybeBox = safeBoundingBox(templateManager, element, position, rotation);
            if (maybeBox.isEmpty()) {
                continue;
            }
            BoundingBox box = maybeBox.get();
            if (collides(box, occupied)) {
                continue;
            }

            return Optional.of(new PoolElementStructurePiece(
                templateManager,
                element,
                position,
                element.getGroundLevelDelta(),
                rotation,
                box
            ));
        }

        return Optional.empty();
    }

    private static boolean collides(BoundingBox box, List<BoundingBox> occupied) {
        BoundingBox inflated = box.inflatedBy(1);
        for (BoundingBox other : occupied) {
            if (inflated.intersects(other)) {
                return true;
            }
        }
        return false;
    }

    private static Rotation rotationForOutward(Direction outward) {
        return switch (outward) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static Rotation[] orderedRotations(Rotation preferred) {
        return switch (preferred) {
            case NONE -> new Rotation[]{Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90, Rotation.CLOCKWISE_180};
            case CLOCKWISE_90 -> new Rotation[]{Rotation.CLOCKWISE_90, Rotation.NONE, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
            case CLOCKWISE_180 -> new Rotation[]{Rotation.CLOCKWISE_180, Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90, Rotation.NONE};
            case COUNTERCLOCKWISE_90 -> new Rotation[]{Rotation.COUNTERCLOCKWISE_90, Rotation.NONE, Rotation.CLOCKWISE_180, Rotation.CLOCKWISE_90};
        };
    }
}

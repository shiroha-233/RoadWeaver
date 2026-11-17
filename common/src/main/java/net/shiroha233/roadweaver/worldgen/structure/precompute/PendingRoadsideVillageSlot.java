package net.shiroha233.roadweaver.worldgen.structure.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * 路边村庄的虚拟拼图接口
 */
public record PendingRoadsideVillageSlot(
    int roadIndex,
    Side side,
    BlockPos anchor,
    Direction outward,
    SlotKind kind,
    int footprintRadius
) {
    public enum Side {
        LEFT,
        RIGHT
    }

    public enum SlotKind {
        HOUSE,
        DECOR,
        VILLAGER
    }

    public Identifier poolId(Identifier style) {
        return switch (kind) {
            case HOUSE -> Identifier.fromNamespaceAndPath("minecraft", "village/" + style.getPath() + "/houses");
            case DECOR -> Identifier.fromNamespaceAndPath("minecraft", "village/" + style.getPath() + "/decor");
            case VILLAGER -> Identifier.fromNamespaceAndPath("minecraft", "village/" + style.getPath() + "/villagers");
        };
    }
}

package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

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

    public ResourceLocation poolId(ResourceLocation style) {
        return switch (kind) {
            case HOUSE -> new ResourceLocation("minecraft", "village/" + style.getPath() + "/houses");
            case DECOR -> new ResourceLocation("minecraft", "village/" + style.getPath() + "/decor");
            case VILLAGER -> new ResourceLocation("minecraft", "village/" + style.getPath() + "/villagers");
        };
    }
}

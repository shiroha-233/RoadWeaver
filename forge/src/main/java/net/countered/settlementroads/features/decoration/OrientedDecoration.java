package net.countered.settlementroads.features.decoration;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;


public abstract class OrientedDecoration extends Decoration{

    private final Vec3i orthogonalVector;

    public OrientedDecoration(BlockPos placePos, Vec3i orthogonalVector, WorldGenLevel world) {
        super(placePos, world);
        this.orthogonalVector = orthogonalVector;
    }

    protected final int getCardinalRotationFromVector(Vec3i orthogonalVector, boolean start) {
        if (start) {
            if (Math.abs(orthogonalVector.getX()) > Math.abs(orthogonalVector.getZ())) {
                return orthogonalVector.getX() > 0 ? 0 : 8; // N or S
            } else {
                return orthogonalVector.getZ() > 0 ? 4 : 12; // E or W
            }
        }
        else {
            if (Math.abs(orthogonalVector.getX()) > Math.abs(orthogonalVector.getZ())) {
                return orthogonalVector.getX() > 0 ? 8 : 0; // N or S
            } else {
                return orthogonalVector.getZ() > 0 ? 12 : 4; // E or W
            }
        }
    }

    public Vec3i getOrthogonalVector() {
        return orthogonalVector;
    }

    protected static class DirectionProperties {
        Direction offsetDirection;
        net.minecraft.world.level.block.state.properties.Property<Boolean> reverseDirectionProperty;
        net.minecraft.world.level.block.state.properties.Property<Boolean> directionProperty;

        DirectionProperties(Direction offset, net.minecraft.world.level.block.state.properties.Property<Boolean> reverse, net.minecraft.world.level.block.state.properties.Property<Boolean> direction) {
            this.offsetDirection = offset;
            this.reverseDirectionProperty = reverse;
            this.directionProperty = direction;
        }
    }

    protected DirectionProperties getDirectionProperties(int rotation) {
        return switch (rotation) {
            case 12 -> new DirectionProperties(Direction.NORTH, BlockStateProperties.NORTH, BlockStateProperties.SOUTH);
            case 0 -> new DirectionProperties(Direction.EAST,  BlockStateProperties.EAST,  BlockStateProperties.WEST);
            case 4 -> new DirectionProperties(Direction.SOUTH, BlockStateProperties.SOUTH, BlockStateProperties.NORTH);
            default -> new DirectionProperties(Direction.WEST,  BlockStateProperties.WEST,  BlockStateProperties.EAST);
        };
    }
}

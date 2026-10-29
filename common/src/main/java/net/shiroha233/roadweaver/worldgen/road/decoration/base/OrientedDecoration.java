package net.shiroha233.roadweaver.worldgen.road.decoration.base;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

/**
 * 定向装饰基类
 */
public abstract class OrientedDecoration extends Decoration {

    private final Vec3i orthogonalVector;

    public OrientedDecoration(BlockPos placePos, Vec3i orthogonalVector, WorldGenLevel world) {
        super(placePos, world);
        this.orthogonalVector = orthogonalVector;
    }

    protected final int getCardinalRotationFromVector(Vec3i vec, boolean start) {
        if (start) {
            if (Math.abs(vec.getX()) > Math.abs(vec.getZ())) {
                return vec.getX() > 0 ? 0 : 8;
            } else {
                return vec.getZ() > 0 ? 4 : 12;
            }
        } else {
            if (Math.abs(vec.getX()) > Math.abs(vec.getZ())) {
                return vec.getX() > 0 ? 8 : 0;
            } else {
                return vec.getZ() > 0 ? 12 : 4;
            }
        }
    }

    public Vec3i getOrthogonalVector() { return orthogonalVector; }

    public static class DirectionProperties {
        public final Direction offsetDirection;
        public final BooleanProperty reverseDirectionProperty;
        public final BooleanProperty directionProperty;

        public DirectionProperties(Direction offset, BooleanProperty reverse, BooleanProperty direction) {
            this.offsetDirection = offset;
            this.reverseDirectionProperty = reverse;
            this.directionProperty = direction;
        }
    }

    protected DirectionProperties getDirectionProperties(int rotation) {
        return switch (rotation) {
            case 12 -> new DirectionProperties(Direction.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.NORTH);
            case 0 -> new DirectionProperties(Direction.EAST, BlockStateProperties.WEST, BlockStateProperties.EAST);
            case 4 -> new DirectionProperties(Direction.SOUTH, BlockStateProperties.NORTH, BlockStateProperties.SOUTH);
            default -> new DirectionProperties(Direction.WEST, BlockStateProperties.EAST, BlockStateProperties.WEST);
        };
    }
}

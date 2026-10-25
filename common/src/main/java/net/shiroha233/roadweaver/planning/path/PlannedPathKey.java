package net.shiroha233.roadweaver.planning.path;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.StructureConnection;

import java.util.Objects;

/**
 * 使用完整端点坐标表达一条无向道路连接。
 */
public record PlannedPathKey(Endpoint first, Endpoint second) {
    public PlannedPathKey {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.compareTo(second) > 0) {
            Endpoint swap = first;
            first = second;
            second = swap;
        }
    }

    public static PlannedPathKey of(StructureConnection connection) {
        Objects.requireNonNull(connection, "connection");
        return of(connection.from(), connection.to());
    }

    public static PlannedPathKey of(BlockPos first, BlockPos second) {
        return new PlannedPathKey(Endpoint.of(first), Endpoint.of(second));
    }

    public record Endpoint(int x, int y, int z) implements Comparable<Endpoint> {
        public static Endpoint of(BlockPos position) {
            Objects.requireNonNull(position, "position");
            return new Endpoint(position.getX(), position.getY(), position.getZ());
        }

        @Override
        public int compareTo(Endpoint other) {
            int xOrder = Integer.compare(x, other.x);
            if (xOrder != 0) return xOrder;
            int yOrder = Integer.compare(y, other.y);
            return yOrder != 0 ? yOrder : Integer.compare(z, other.z);
        }
    }
}

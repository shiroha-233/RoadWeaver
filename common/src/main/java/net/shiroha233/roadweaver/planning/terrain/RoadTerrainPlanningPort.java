package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.core.model.StructureConnection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface RoadTerrainPlanningPort {
    Result plan(Request request);

    record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public Bounds {
            if (maxX < minX || maxZ < minZ) {
                throw new IllegalArgumentException("invalid planning bounds");
            }
        }
    }

    record Request(ServerLevel level,
                   Bounds bounds,
                   List<StructureConnection> connections,
                   PathfindingCostConfig pathfinding) {
        public Request {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(bounds, "bounds");
            connections = List.copyOf(connections == null ? List.of() : connections);
            PathfindingCostConfig snapshot = Objects.requireNonNull(pathfinding, "pathfinding").snapshot();
            snapshot.sanitize();
            pathfinding = snapshot;
        }
    }

    record Result(TerrainSamplingMode mode,
                  Map<StructureConnection, List<net.minecraft.core.BlockPos>> paths) {
        public Result {
            Objects.requireNonNull(mode, "mode");
            LinkedHashMap<StructureConnection, List<net.minecraft.core.BlockPos>> copy = new LinkedHashMap<>();
            if (paths != null) {
                paths.forEach((connection, path) -> {
                    if (connection != null && path != null && !path.isEmpty()) {
                        copy.put(connection, List.copyOf(path));
                    }
                });
            }
            paths = Map.copyOf(copy);
        }

        public static Result empty(TerrainSamplingMode mode) {
            return new Result(mode, Map.of());
        }
    }
}

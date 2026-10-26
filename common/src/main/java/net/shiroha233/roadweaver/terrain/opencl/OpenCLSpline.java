package net.shiroha233.roadweaver.terrain.opencl;

import java.util.List;

/**
 * OpenCL 侧 CubicSpline 描述。
 */
public record OpenCLSpline(
        int coordinateNode,
        List<Double> locations,
        List<Integer> valueNodes,
        List<Double> derivatives
) {
    public OpenCLSpline {
        locations = List.copyOf(locations == null ? List.of() : locations);
        valueNodes = List.copyOf(valueNodes == null ? List.of() : valueNodes);
        derivatives = List.copyOf(derivatives == null ? List.of() : derivatives);
        if (locations.size() != valueNodes.size() || locations.size() != derivatives.size()) {
            throw new IllegalArgumentException("spline table sizes must match");
        }
    }

    public int pointCount() {
        return locations.size();
    }
}
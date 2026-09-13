/* 文件职责：验证精采多根 density graph 的去重与 marker 依赖顺序。 */
package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DensityGraphCompilerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sharesNodesAcrossRootsAndPreservesInterpolatedMarker() {
        DensityFunction shared = DensityFunctions.constant(2.0);
        DensityFunction interpolated = DensityFunctions.interpolated(shared);
        DensityFunction finalDensity = DensityFunctions.add(interpolated, shared);
        EnumMap<DensityGraphRoot, DensityFunction> roots = new EnumMap<>(DensityGraphRoot.class);
        roots.put(DensityGraphRoot.FINAL_DENSITY, finalDensity);
        roots.put(DensityGraphRoot.INITIAL_DENSITY_WITHOUT_JAGGEDNESS, shared);
        roots.put(DensityGraphRoot.EROSION, DensityFunctions.cache2d(shared));

        DensityGraphCompileResult result = DensityGraphCompiler.compile(roots);
        assertTrue(result.supported(), result.unsupportedReason());
        DensityGraphProgram program = result.program();
        assertEquals(4, program.nodes().size());
        assertEquals(program.root(DensityGraphRoot.INITIAL_DENSITY_WITHOUT_JAGGEDNESS),
                program.root(DensityGraphRoot.EROSION));
        assertEquals(1, program.interpolatedNodes().size());

        int markerIndex = program.interpolatedNodes().get(0);
        DensityGraphNode marker = program.nodes().get(markerIndex);
        assertEquals(DensityGraphNodeType.INTERPOLATED, marker.type());
        assertTrue(marker.left() < markerIndex);
        OpenCLDensityProgramPayload payload = OpenCLDensityProgramPayload.from(program);
        int markerUsage = payload.nodeInts()[markerIndex * 6 + 5];
        assertTrue((markerUsage & 1) != 0, "final density must depend on the marker");
        assertTrue((markerUsage & 256) != 0, "lattice generation must evaluate the marker's wrapped graph");
        DensityGraphNode root = program.nodes().get(program.root(DensityGraphRoot.FINAL_DENSITY));
        assertTrue(root.left() < program.root(DensityGraphRoot.FINAL_DENSITY));
        assertTrue(root.right() < program.root(DensityGraphRoot.FINAL_DENSITY));
    }
}

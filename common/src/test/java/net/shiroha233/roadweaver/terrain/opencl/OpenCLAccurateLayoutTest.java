/* 文件职责：验证精采 lattice 索引顺序与三线性插值语义。 */
package net.shiroha233.roadweaver.terrain.opencl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenCLAccurateLayoutTest {
    @Test
    void interpolatesInVanillaXThenYThenZOrder() {
        int cellCountXZ = 1;
        int cellCountY = 1;
        double[] lattice = new double[OpenCLAccurateLayout.latticePointCount(cellCountXZ, cellCountY)];
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                for (int y = 0; y <= 1; y++) {
                    lattice[OpenCLAccurateLayout.latticePointIndex(cellCountXZ, cellCountY, x, y, z)] =
                            x + y * 10.0 + z * 100.0;
                }
            }
        }

        assertEquals(80.25, OpenCLAccurateLayout.interpolateCell(
                lattice, cellCountXZ, cellCountY, 0, 0, 0, 0.25, 0.5, 0.75), 1.0E-12);
    }
}

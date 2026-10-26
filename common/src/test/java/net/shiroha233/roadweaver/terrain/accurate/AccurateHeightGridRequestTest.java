/* 文件职责：验证大区域精采网格的坐标展开、负坐标与数组边界契约。 */
package net.shiroha233.roadweaver.terrain.accurate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccurateHeightGridRequestTest {
    @Test
    void expandsRowMajorGridAcrossNegativeCoordinates() {
        AccurateHeightGridRequest request = new AccurateHeightGridRequest(-24, -16, 3, 2, 8);

        assertEquals(6, request.sampleCount());
        assertEquals(-24, request.blockX(0));
        assertEquals(-16, request.blockZ(0));
        assertEquals(-8, request.blockX(2));
        assertEquals(-16, request.blockZ(2));
        assertEquals(-24, request.blockX(3));
        assertEquals(-8, request.blockZ(3));
        assertEquals(-8, request.blockX(5));
        assertEquals(-8, request.blockZ(5));
    }

    @Test
    void rejectsInvalidDimensionsAndIndexes() {
        assertThrows(IllegalArgumentException.class,
                () -> new AccurateHeightGridRequest(0, 0, 0, 1, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new AccurateHeightGridRequest(0, 0, 1, 1, 0));

        AccurateHeightGridRequest request = new AccurateHeightGridRequest(0, 0, 2, 2, 8);
        assertThrows(IndexOutOfBoundsException.class, () -> request.blockX(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> request.blockZ(4));
    }
}

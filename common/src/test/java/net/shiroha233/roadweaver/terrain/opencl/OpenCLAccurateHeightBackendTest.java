/* 文件职责：验证精采后端校验只抽取有限且空间分散的代表 chunk。 */
package net.shiroha233.roadweaver.terrain.opencl;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenCLAccurateHeightBackendTest {
    @Test
    void validationSelectionIsLimitedAndSpatiallyDispersed() {
        List<Long> chunks = new ArrayList<>();
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }

        Collection<Long> selected = OpenCLAccurateHeightBackend.selectValidationKeys(chunks);

        assertEquals(4, selected.size());
        assertTrue(selected.contains(ChunkPos.asLong(0, 0)));
        assertTrue(selected.stream().anyMatch(key -> ChunkPos.getX(key) >= 24 && ChunkPos.getZ(key) >= 24));
    }
}

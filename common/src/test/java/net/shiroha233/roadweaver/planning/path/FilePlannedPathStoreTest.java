/* 文件职责：验证待生成路径文件的往返、重启复用、指纹隔离与删除。 */
package net.shiroha233.roadweaver.planning.path;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.shiroha233.roadweaver.core.model.PlannedPathKey;

class FilePlannedPathStoreTest {
    @TempDir
    Path directory;

    @Test
    void pathSurvivesStoreRecreationAndIsDeletedOnlyExplicitly() throws Exception {
        PlannedPathKey key = PlannedPathKey.of(
                new BlockPos(-30, 65, 12),
                new BlockPos(400, 72, -81));
        List<BlockPos> path = List.of(
                new BlockPos(-30, 68, 12),
                new BlockPos(8, 70, -4),
                new BlockPos(400, 73, -81));

        new FilePlannedPathStore(directory).save(key, "fingerprint-a", path);
        FilePlannedPathStore restarted = new FilePlannedPathStore(directory);

        assertEquals(path, restarted.load(key, "fingerprint-a").orElseThrow());
        assertTrue(restarted.load(key, "fingerprint-b").isEmpty());
        restarted.delete(key);
        assertTrue(restarted.load(key, "fingerprint-a").isEmpty());
    }

    @Test
    void differentEndpointPairCannotReadAnotherPath() throws Exception {
        PlannedPathKey stored = PlannedPathKey.of(new BlockPos(0, 0, 0), new BlockPos(0, 0, 5));
        PlannedPathKey other = PlannedPathKey.of(new BlockPos(0, 0, 4), new BlockPos(0, 0, 7));
        FilePlannedPathStore store = new FilePlannedPathStore(directory);
        store.save(stored, "same", List.of(new BlockPos(0, 64, 0), new BlockPos(0, 64, 5)));

        assertFalse(store.load(other, "same").isPresent());
    }
}

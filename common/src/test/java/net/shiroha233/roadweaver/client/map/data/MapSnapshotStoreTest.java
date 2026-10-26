/* 文件职责：验证地图快照仓库可以重新加载主动采样区域的结构数据。 */
package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.map.data.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.MapViewportController;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.shiroha233.roadweaver.map.data.MapSnapshot;
import net.shiroha233.roadweaver.map.data.RequestRect;

class MapSnapshotStoreTest {
    @Test
    void clearingSampledStructureRectAllowsASecondLoad() {
        MapSnapshotStore store = new MapSnapshotStore();
        RequestRect loaded = new RequestRect(0, 0, 64, 64);
        BlockPos structure = new BlockPos(32, 0, 32);
        store.merge(
                MapLoadPhase.STRUCTURES,
                loaded,
                new MapSnapshot(
                        List.of(structure),
                        List.of(),
                        List.of(new StructureInfo(structure, "minecraft:village")),
                        List.of()));

        assertEquals(List.of(loaded), store.loadedRects(MapLoadPhase.STRUCTURES));
        store.clearRect(MapLoadPhase.STRUCTURES, loaded);

        assertTrue(store.loadedRects(MapLoadPhase.STRUCTURES).isEmpty());
        assertTrue(store.snapshot().structures().isEmpty());
    }
}

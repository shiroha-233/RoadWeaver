package net.shiroha233.roadweaver.pathfinding.cache;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 精确高度样本持久化端口。
 */
public interface AccurateSampleStore extends AutoCloseable {
    Map<Long, AccurateHeightChunk> loadChunks(Collection<Long> chunkKeys);

    Map<Long, AccurateHeightSample> loadSamples(Collection<Long> sampleKeys);

    void saveChunks(Map<Long, AccurateHeightChunk> chunks);

    void saveSamples(Map<Long, AccurateHeightSample> samples);

    default void saveGrid(AccurateHeightGrid grid) {
        if (grid == null) {
            return;
        }
        LinkedHashMap<Long, AccurateHeightSample> samples = new LinkedHashMap<>(grid.request().sampleCount());
        for (int index = 0; index < grid.request().sampleCount(); index++) {
            samples.put(
                    AccurateHeightSample.key(grid.request().blockX(index), grid.request().blockZ(index)),
                    new AccurateHeightSample(
                            grid.worldSurface()[index],
                            grid.oceanFloor()[index],
                            grid.motionBlocking()[index]));
        }
        saveSamples(samples);
    }

    @Override
    default void close() {}

    static AccurateSampleStore noop() {
        return NoOpAccurateSampleStore.INSTANCE;
    }

    final class NoOpAccurateSampleStore implements AccurateSampleStore {
        private static final NoOpAccurateSampleStore INSTANCE = new NoOpAccurateSampleStore();

        private NoOpAccurateSampleStore() {}

        @Override
        public Map<Long, AccurateHeightChunk> loadChunks(Collection<Long> chunkKeys) {
            return Map.of();
        }

        @Override
        public Map<Long, AccurateHeightSample> loadSamples(Collection<Long> sampleKeys) {
            return Map.of();
        }

        @Override
        public void saveChunks(Map<Long, AccurateHeightChunk> chunks) {}

        @Override
        public void saveSamples(Map<Long, AccurateHeightSample> samples) {}
    }
}

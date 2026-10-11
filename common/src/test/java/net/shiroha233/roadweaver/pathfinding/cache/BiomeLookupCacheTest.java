package net.shiroha233.roadweaver.pathfinding.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeLookupCacheTest {
    private static final class CountingCache extends BiomeLookupCache<String> {
        private final AtomicInteger lookups = new AtomicInteger();
        private final CountDownLatch entered;
        private final CountDownLatch release;

        CountingCache(int capacity) {
            this(capacity, null, null);
        }

        CountingCache(int capacity, CountDownLatch entered, CountDownLatch release) {
            super(capacity);
            this.entered = entered;
            this.release = release;
        }

        @Override
        protected String sample(int quartX, int quartZ) {
            lookups.incrementAndGet();
            if (entered != null) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return quartX + ":" + quartZ;
        }
    }

    @Test
    void sharesQuartCellsAcrossAdjacentBlocks() {
        CountingCache cache = new CountingCache(64);
        assertEquals("-1:-1", cache.get(-4, -1));
        assertEquals("-1:-1", cache.get(-1, -4));
        assertEquals("-1:-1", cache.get(-2, -2));
        assertEquals(1, cache.lookups.get(), "one 4x4 biome cell must only be sampled once");

        assertEquals("0:0", cache.get(0, 0));
        assertEquals(2, cache.lookups.get());
    }

    @Test
    void evictsLeastRecentlyUsedWhenOverCapacity() {
        CountingCache cache = new CountingCache(64);
        String first = cache.get(0, 0);
        for (int i = 1; i <= 512; i++) {
            cache.get(i * 4, 0);
        }
        assertEquals(first, cache.get(0, 0), "recently read entries must survive eviction");
    }

    @Test
    void concurrentRequestsForOneCellShareOneLookup() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountingCache cache = new CountingCache(64, entered, release);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> cache.get(4, 4));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> cache.get(7, 7));
            release.countDown();
            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, cache.lookups.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsCapacityBelowShardCount() {
        assertThrows(IllegalArgumentException.class, () -> new CountingCache(63));
    }
}
/* 文件职责：验证地形采样会话的降级与注册表重置行为。 */
package net.shiroha233.roadweaver.planning.pipeline;

import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainSamplingSessionTest {

    @AfterEach
    void tearDown() {
        TerrainSamplingSessions.clearAll();
    }

    @Test
    void downgradeFromFullRegionIsIdempotent() {
        TerrainSamplingSession session = new TerrainSamplingSession(TerrainSamplingMode.FULL_REGION);
        session.recordBackend("OPENCL_ACCURATE", "GPU-0");

        assertTrue(session.downgrade("opencl_unavailable", "CPU", ""));
        assertFalse(session.downgrade("another_reason", "OPENCL_ACCURATE", "GPU-0"));

        TerrainSamplingSessionSnapshot snapshot = session.snapshot();
        assertEquals(TerrainSamplingMode.FULL_REGION, snapshot.configuredMode());
        assertEquals(TerrainSamplingMode.COARSE_CORRIDOR, snapshot.effectiveMode());
        assertEquals("CPU", snapshot.backend());
        assertEquals("", snapshot.device());
        assertEquals("opencl_unavailable", snapshot.fallbackReason());
        assertTrue(snapshot.downgraded());
    }

    @Test
    void recordBackendDoesNotChangeModeOrFallback() {
        TerrainSamplingSession session = new TerrainSamplingSession(TerrainSamplingMode.FULL_REGION);

        session.recordBackend("OPENCL_ACCURATE", "GPU-1");

        TerrainSamplingSessionSnapshot snapshot = session.snapshot();
        assertEquals(TerrainSamplingMode.FULL_REGION, snapshot.configuredMode());
        assertEquals(TerrainSamplingMode.FULL_REGION, snapshot.effectiveMode());
        assertEquals("OPENCL_ACCURATE", snapshot.backend());
        assertEquals("GPU-1", snapshot.device());
        assertEquals("", snapshot.fallbackReason());
        assertFalse(snapshot.downgraded());
    }

    @Test
    void clearKeyCreatesFreshSession() {
        Object key = new Object();
        TerrainSamplingSession first = TerrainSamplingSessions.forKey(key, TerrainSamplingMode.FULL_REGION);
        first.downgrade("validation_failed", "CPU", "fallback-device");

        TerrainSamplingSession same = TerrainSamplingSessions.forKey(key, TerrainSamplingMode.COARSE_CORRIDOR);
        assertSame(first, same);
        assertEquals(TerrainSamplingMode.FULL_REGION, same.configuredMode());

        TerrainSamplingSessions.clearKey(key);
        TerrainSamplingSession reset = TerrainSamplingSessions.forKey(key, TerrainSamplingMode.COARSE_CORRIDOR);

        assertNotSame(first, reset);
        assertEquals(TerrainSamplingMode.COARSE_CORRIDOR, reset.configuredMode());
        assertEquals(TerrainSamplingMode.COARSE_CORRIDOR, reset.effectiveMode());
        assertEquals("", reset.fallbackReason());
    }

    @Test
    void clearAllDropsAllRegisteredSessions() {
        TerrainSamplingSessions.forKey(new Object(), TerrainSamplingMode.COARSE_CORRIDOR);
        TerrainSamplingSessions.forKey(new Object(), TerrainSamplingMode.FULL_REGION);

        assertEquals(2, TerrainSamplingSessions.sizeForTests());

        TerrainSamplingSessions.clearAll();

        assertEquals(0, TerrainSamplingSessions.sizeForTests());
    }
}

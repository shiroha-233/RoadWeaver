/* 文件职责：验证 OpenCL 全局回退状态可在新服务器会话开始时重置。 */
package net.shiroha233.roadweaver.terrain.opencl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenCLAvailabilityTest {
    @AfterEach
    void resetAvailability() {
        OpenCLAvailability.resetForTests();
    }

    @Test
    void resetRestoresAvailabilityAfterPreviousSessionFailure() {
        OpenCLAvailability.disable("test failure");
        assertFalse(OpenCLAvailability.isAvailable());

        OpenCLAvailability.reset();

        assertTrue(OpenCLAvailability.isAvailable());
    }
}

package com.aurora.client.util.reflex;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticReflexPolicyTest {
    @Test void pacingNeedsWarmMeasurementsAndQueuedGpuBoundWorkAndIsBounded() {
        assertEquals(0, AutomaticReflexPolicy.waitBudget(1_000_000, 10_000_000, 29, 3));
        assertEquals(0, AutomaticReflexPolicy.waitBudget(10_000_000, 1_000_000, 60, 3));
        assertEquals(0, AutomaticReflexPolicy.waitBudget(1_000_000, 10_000_000, 60, 1));
        assertEquals(2_000_000, AutomaticReflexPolicy.waitBudget(1_000_000, 10_000_000, 60, 3));
        assertEquals(600_000, AutomaticReflexPolicy.waitBudget(3_000_000, 4_000_000, 60, 3));
    }

    @Test void transientProblemsRecoverButPersistentFailuresLatchOff() {
        var timings = new AutomaticReflexPolicy();
        for (int i = 0; i < 29; i++) assertFalse(timings.acceptGpuSample(-1));
        assertFalse(timings.failed());
        assertTrue(timings.acceptGpuSample(10_000_000));
        assertFalse(timings.failed());
        for (int i = 0; i < 30; i++) timings.acceptGpuSample(300_000_000);
        assertTrue(timings.failed());
        var queue = new AutomaticReflexPolicy();
        for (int i = 0; i < 119; i++) queue.observeQueue(9);
        assertFalse(queue.failed());
        queue.observeQueue(1);
        for (int i = 0; i < 120; i++) queue.observeQueue(9);
        assertTrue(queue.failed());
    }
}

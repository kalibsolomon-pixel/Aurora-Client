package com.aurora.client.util.reflex;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReflexSchedulerTest {
    @Test void aHookFailureLatchesPacingOffAndNeverEscapesIntoMinecraft() {
        var scheduler = new ReflexScheduler();
        assertDoesNotThrow(() -> scheduler.runGuarded(() -> { throw new IllegalStateException("test failure"); }));
        assertTrue(scheduler.isSessionDisabled());
        scheduler.runGuarded(() -> fail("Disabled pacing must never execute another hook"));
    }

    @Test void gpuWarmupEstimateIsNormalizedAndRejectsInvalidSamples() {
        var scheduler = new ReflexScheduler();
        scheduler.updateGpuTime(10_000_000);
        assertEquals(10_000_000L, scheduler.getEstimateGpuTime(), 2);
        scheduler.updateGpuTime(-1);
        assertEquals(10_000_000L, scheduler.getEstimateGpuTime(), 2);
        for (int i = 0; i < 60; i++) scheduler.updateGpuTime(5_000_000);
        assertEquals(5_000_000L, scheduler.getEstimateGpuTime(), 5);
    }
}

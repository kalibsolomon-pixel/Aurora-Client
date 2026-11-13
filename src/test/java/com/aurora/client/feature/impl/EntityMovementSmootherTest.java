package com.aurora.client.feature.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Easing between frames, and the snap that keeps an entity from sliding back into view. */
class EntityMovementSmootherTest {

    private static final long FRAME = 16_000_000L;

    @Test
    void easesTowardTheTargetBetweenConsecutiveFrames() {
        var s = new EntityMovementSmoother.State();
        assertFalse(EntityMovementSmoother.smooth(s, 0, 0, 0, 0.05, 8, 1_000_000_000L));
        assertTrue(EntityMovementSmoother.smooth(s, 1, 0, 0, 0.05, 8, 1_000_000_000L + FRAME));
        assertTrue(s.x > 0 && s.x < 1, "eased part of the way: " + s.x);
    }

    @Test
    void snapsAfterTheEntityWentUnrendered() {
        var s = new EntityMovementSmoother.State();
        long t = 1_000_000_000L;
        EntityMovementSmoother.smooth(s, 0, 0, 0, 0.05, 8, t);
        t += EntityMovementSmoother.STALE_NANOS + FRAME;
        assertFalse(EntityMovementSmoother.smooth(s, 3, 0, 0, 0.05, 8, t));
        assertEquals(3, s.x);
        // And eases normally from there.
        assertTrue(EntityMovementSmoother.smooth(s, 4, 0, 0, 0.05, 8, t + FRAME));
        assertTrue(s.x > 3 && s.x < 4);
    }

    @Test
    void teleportClassJumpsSnap() {
        var s = new EntityMovementSmoother.State();
        EntityMovementSmoother.smooth(s, 0, 0, 0, 0.05, 8, 1_000_000_000L);
        assertFalse(EntityMovementSmoother.smooth(s, 20, 0, 0, 0.05, 8, 1_000_000_000L + FRAME));
        assertEquals(20, s.x);
    }
}

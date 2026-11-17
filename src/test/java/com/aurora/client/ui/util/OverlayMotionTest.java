package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OverlayMotionTest {
    private static final long MS = 1_000_000;

    @Test
    void restIsUnscaledAtExactPixelsAndClosingFinishesInNinetyMillis() {
        var motion = new OverlayMotion();
        motion.setOpen(true, 0);
        assertTrue(motion.progress(60 * MS, false) > .5f);
        float rest = motion.progress(120 * MS, false);
        assertEquals(1f, rest);
        assertEquals(1f, OverlayMotion.scale(rest));
        assertEquals(37, OverlayMotion.unscale(37, 14, rest));
        motion.setOpen(false, 150 * MS);
        assertTrue(motion.progress(195 * MS, false) < .5f);
        assertEquals(0f, motion.progress(240 * MS, false));
    }

    @Test
    void reversalContinuesFromCurrentProgressAndReducedMotionSnaps() {
        var motion = new OverlayMotion();
        motion.setOpen(true, 0);
        assertEquals(1f, motion.progress(0, true));
        float opening = motion.progress(35 * MS, false);
        motion.setOpen(false, 35 * MS);
        assertEquals(opening, motion.progress(35 * MS, false));
        assertEquals(0f, motion.progress(35 * MS, true));
        motion.reset();
        assertEquals(0f, motion.progress(36 * MS, false));
    }
}

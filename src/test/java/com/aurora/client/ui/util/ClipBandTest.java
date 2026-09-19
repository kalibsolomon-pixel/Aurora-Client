package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-1 primitive pins: the shared viewport band's intersection
 * semantics are half-open and edge-contact is NOT intersection. Every
 * host-side coupling (scissor == cull == pointer == hover == keyboard
 * availability) builds on exactly these answers, so ambiguity here would
 * become input/render drift everywhere.
 */
class ClipBandTest {

    // Reference band: x [10, 40), y [100, 200) — 30x100.
    private final ClipBand band = new ClipBand(10, 100, 30, 100);

    @Test
    void containsIsHalfOpen() {
        assertTrue(band.contains(10, 100), "inclusive origin");
        assertTrue(band.contains(39.9, 199.9), "just inside the exclusive edges");
        assertFalse(band.contains(40, 150), "x == xEnd is outside (half-open)");
        assertFalse(band.contains(150, 200), "y == yEnd is outside (half-open)");
        assertFalse(band.contains(9.9, 150), "left of the band");
        assertFalse(band.contains(150, 99.9), "above the band");
    }

    @Test
    void fullyInsideRectIntersects() {
        assertTrue(band.intersects(12, 120, 10, 20));
        assertTrue(band.contains(12, 120), "an inside rect's pixels are actionable");
    }

    @Test
    void fullyOutsideRectsNeverIntersect() {
        assertFalse(band.intersects(0, 0, 10, 100), "fully left (edge contact, not overlap)");
        assertFalse(band.intersects(40, 100, 30, 100), "fully right (edge contact)");
        assertFalse(band.intersects(10, 0, 30, 100), "fully above (edge contact)");
        assertFalse(band.intersects(10, 200, 30, 100), "fully below (edge contact)");
        assertFalse(band.intersects(-50, -50, 5, 5), "far away");
    }

    @Test
    void partialOverlapIntersectsOnEverySide() {
        assertTrue(band.intersects(0, 120, 20, 10), "partial left");
        assertTrue(band.intersects(30, 120, 30, 10), "partial right");
        assertTrue(band.intersects(12, 50, 10, 100), "partial top");
        assertTrue(band.intersects(12, 150, 10, 100), "partial bottom");
    }

    @Test
    void exactEdgeContactIsNotIntersection() {
        // Touching without sharing a pixel: the half-open convention makes
        // these empty so a row whose bottom edge equals the viewport top is
        // NOT rendered/clickable there (the row's last pixel is yTop-1).
        assertFalse(band.intersects(10, 200, 30, 50), "bottom edge exactly at band top");
        assertFalse(band.intersects(10, 50, 30, 50), "top edge exactly at band bottom");
        assertFalse(band.intersects(40, 100, 30, 100), "left edge exactly at band right");
        assertFalse(band.intersects(0, 100, 10, 100), "right edge exactly at band left");
    }

    @Test
    void bandExactlyCoveringRectIntersects() {
        assertTrue(band.intersects(10, 100, 30, 100), "identical geometry overlaps fully");
    }

    @Test
    void zeroSizeRectsNeverIntersect() {
        assertFalse(band.intersects(15, 120, 0, 10), "zero width");
        assertFalse(band.intersects(15, 120, 10, 0), "zero height");
        assertFalse(band.intersects(15, 120, 0, 0));
    }

    @Test
    void degenerateBandsAreEmptyAndInert() {
        ClipBand zeroW = new ClipBand(10, 100, 0, 100);
        ClipBand zeroH = new ClipBand(10, 100, 30, 0);
        ClipBand negative = new ClipBand(10, 100, -5, -5);
        for (ClipBand b : new ClipBand[]{zeroW, zeroH, negative}) {
            assertTrue(b.isEmpty());
            assertFalse(b.contains(10, 100), "origin of a degenerate band is not inside");
            assertFalse(b.intersects(0, 0, 1000, 1000), "nothing intersects a degenerate band");
        }
    }

    @Test
    void clampYPullsIntoTheClosedRange() {
        assertEquals(100, band.clampY(-20), 0.0);
        assertEquals(100, band.clampY(100), 0.0);
        assertEquals(150, band.clampY(150), 0.0);
        assertEquals(200, band.clampY(250), 0.0, "clamps to the far edge, not past it");
        assertEquals(200, band.clampY(200), 0.0);
    }
}

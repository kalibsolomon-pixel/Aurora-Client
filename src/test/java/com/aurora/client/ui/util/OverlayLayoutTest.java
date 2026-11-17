package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OverlayLayoutTest {
    @Test
    void tooltipPrefersBelowRightAndFlipsOnEachAxis() {
        assertAt(44, 34, OverlayLayout.tooltip(0, 0, 200, 150, 20, 20, 20, 10, 60, 30));
        assertAt(101, 34, OverlayLayout.tooltip(0, 0, 200, 150, 165, 20, 20, 10, 60, 30));
        assertAt(101, 96, OverlayLayout.tooltip(0, 0, 200, 150, 165, 130, 20, 10, 60, 30));
    }

    @Test
    void tooltipClampsToMarginWithoutCoveringItsAnchor() {
        assertAt(8, 39, OverlayLayout.tooltip(0, 0, 100, 100, 5, 20, 65, 15, 68, 30));
        assertAt(74, 28, OverlayLayout.tooltip(0, 0, 150, 80, 30, 28, 40, 30, 60, 32));
        assertEquals(OverlayLayout.NO_POSITION, OverlayLayout.tooltip(0, 0, 100, 80, 8, 28, 84, 30, 60, 32));
    }

    @Test
    void popupFitsBelowFlipsAboveAndClampsToTheHost() {
        assertEquals(90, OverlayLayout.popupTop(90, 68, 50, 10, 140));
        assertEquals(18, OverlayLayout.popupTop(90, 68, 50, 10, 139));
        assertEquals(10, OverlayLayout.popupTop(70, 48, 50, 10, 80));
    }

    @Test
    void focusRevealTargetsOnlyTheDistanceNeededAndClampsAtBothEnds() {
        assertEquals(0, OverlayLayout.scrollToReveal(0, 200, 30, 50, 10, 90, 8));
        assertEquals(28, OverlayLayout.scrollToReveal(0, 200, 90, 110, 10, 90, 8));
        assertEquals(22, OverlayLayout.scrollToReveal(60, 200, 40, 60, 10, 90, 8));
        assertEquals(0, OverlayLayout.scrollToReveal(20, 200, 10, 30, 10, 90, 8));
        assertEquals(200, OverlayLayout.scrollToReveal(100, 200, 280, 290, 10, 90, 8));
        assertEquals(20, OverlayLayout.scrollToReveal(0, 200, 30, 120, 10, 90, 8));
    }

    private static void assertAt(int x, int y, long position) {
        assertNotEquals(OverlayLayout.NO_POSITION, position);
        assertEquals(x, OverlayLayout.x(position));
        assertEquals(y, OverlayLayout.y(position));
    }
}

package com.aurora.client.worldmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkerLabelLayoutTest {
    @Test void fixedCompassChromeExcludesLabelsWithoutMovingTheChrome() {
        MarkerLabelLayout layout = new MarkerLabelLayout();
        layout.reserve(40, 10, 20, 20);
        assertFalse(layout.place(50, 30, 12, 12, 200, 100));
        assertTrue(layout.place(50, 60, 12, 12, 200, 100));
        assertEquals(50, layout.centerX()); assertEquals(60, layout.bottomY());
        layout.reserve(120, 10, 20, 20);
        assertEquals(50, layout.centerX()); assertEquals(60, layout.bottomY());
        assertFalse(layout.place(50, 60, 12, 12, 200, 100));
    }
    @Test void exclusionsAreBoundedAndResetForTheNextFrame() {
        MarkerLabelLayout layout = new MarkerLabelLayout();
        layout.reserve(Float.NaN, 0, 10, 10);
        layout.reserve(0, 0, -10, 10);
        assertTrue(layout.place(50, 50, 10, 10, 200, 100));
        layout.reset();
        for (int i = 0; i < 1000; i++) layout.reserve(i, i, 1, 1);
        layout.reserve(400, 100, 10, 10); // overflow must not allocate/grow the obstacle set
        assertFalse(layout.place(150, 150, 10, 10, 500, 300));
        assertTrue(layout.place(400, 100, 10, 10, 500, 300));
        layout.reset(); assertTrue(layout.place(150, 80, 10, 10, 200, 100));
    }
    @Test void markerExclusionsDoNotConsumeTheBoundedLabelBudget() {
        MarkerLabelLayout layout = new MarkerLabelLayout();
        for (int i = 0; i < 128; i++) layout.reserve(i * 2, 0, 1, 1);
        for (int i = 0; i < 64; i++) assertTrue(layout.place(20 + i * 30, 100, 10, 10, 3000, 300));
        assertFalse(layout.place(2500, 100, 10, 10, 3000, 300));
    }
    @Test void nearbyBeamsCannotCoverTheCamera() {
        assertEquals(0, com.aurora.client.hud.WaypointRenderer.beamOpacity(0));
        assertEquals(0, com.aurora.client.hud.WaypointRenderer.beamOpacity(2));
        assertEquals(.5, com.aurora.client.hud.WaypointRenderer.beamOpacity(5));
        assertEquals(1, com.aurora.client.hud.WaypointRenderer.beamOpacity(8));
        assertEquals(1, com.aurora.client.hud.WaypointRenderer.beamOpacity(1000));
    }
    @Test void labelsStayInsideSmallScreensAndOverlapsAreRejected() {
        MarkerLabelLayout layout = new MarkerLabelLayout();
        assertTrue(layout.place(-500, -500, 150, 48, 240, 160));
        assertEquals(79, layout.centerX()); assertEquals(52, layout.bottomY());
        assertFalse(layout.place(0, 0, 150, 48, 240, 160));
        assertTrue(layout.place(200, 150, 80, 24, 240, 160));
        assertEquals(196, layout.centerX());
        layout.reset();
        assertTrue(layout.place(0, 0, 150, 48, 240, 160));
        assertFalse(layout.place(0, 0, 300, 48, 240, 160));
        assertFalse(layout.place(Float.NaN, 0, 10, 10, 240, 160));
    }
}

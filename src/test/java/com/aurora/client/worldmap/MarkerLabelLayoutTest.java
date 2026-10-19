package com.aurora.client.worldmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkerLabelLayoutTest {
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

package com.aurora.client.worldmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapMarkerDirectionTest {
    @Test void headingUpCancellationNoiseCannotInvalidateTheSameArrow() {
        int expected = MapMarkers.directionCode(0, -1);
        for (double yaw = -2000; yaw <= 2000; yaw += .37) {
            double heading = Math.toRadians(yaw) - (Math.toRadians(yaw) + Math.PI);
            assertEquals(expected, MapMarkers.directionCode(-Math.sin(heading), Math.cos(heading)));
        }
    }
    @Test void wrappingIsContinuousWhileVisiblyDifferentDirectionsStayDistinct() {
        assertEquals(MapMarkers.directionCode(-1, 0), MapMarkers.directionCode(-1, -0.0));
        int up = MapMarkers.directionCode(0, -1), right = MapMarkers.directionCode(1, 0);
        assertNotEquals(up, right);
        double angle = Math.toRadians(1);
        assertNotEquals(right, MapMarkers.directionCode(Math.cos(angle), Math.sin(angle)));
    }
}

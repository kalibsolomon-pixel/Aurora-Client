package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoxDownsampleTest {
    /** The real radius-9 case: 480 + 18 capture pixels -> 249 -> 125 texels. */
    @Test void movingAlternatingDetailCannotLeakThroughRoundedSecondStage() {
        for (int texture : new int[]{249, 512}) { // exact portable and grow-only GL allocations
            float offset = BoxDownsample.halfTexel(249, 125, texture) * texture;
            double unfilteredWorst = 0;
            for (int pixel = 2; pixel < 123; pixel++) {
                double center = (pixel + .5) * 249 / 125 - .5;
                for (int phase = 0; phase < 2; phase++) {
                    double result = (alternating(center - offset, phase) + alternating(center + offset, phase)) / 2;
                    assertEquals(.5, result, 1e-6, "four-pixel source detail must average out during motion");
                    unfilteredWorst = Math.max(unfilteredWorst, Math.abs(alternating(center, phase) - .5));
                }
            }
            assertTrue(unfilteredWorst > .45, "the fixture must detect the original point-sampling alias");
        }
    }

    @Test void exactTwoToOneAndNonMinifyingPassesKeepTheirSingleTap() {
        assertEquals(0, BoxDownsample.halfTexel(248, 124, 512));
        assertEquals(0, BoxDownsample.halfTexel(4, 4, 4));
        assertEquals(0, BoxDownsample.halfTexel(4, 8, 4));
    }

    private static double alternating(double coordinate, int phase) {
        int left = (int) Math.floor(coordinate);
        double fraction = coordinate - left;
        int a = (left + phase) & 1;
        return a * (1 - fraction) + (1 - a) * fraction;
    }
}
